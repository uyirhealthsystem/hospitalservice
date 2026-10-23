package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.HospitalSeatResponse;
import com.uyir.hospital.dto.SeatPurchaseResponse;
import com.uyir.hospital.dto.SeatSummaryResponse;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.HospitalSeat;
import com.uyir.hospital.model.SeatPurchase;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.SeatPurchaseStatus;
import com.uyir.hospital.model.enums.SeatType;
import com.uyir.hospital.payment.PaymentClient;
import com.uyir.hospital.payment.PaymentClient.Intent;
import com.uyir.hospital.payment.PaymentClient.PaymentStatus;
import com.uyir.hospital.payment.PaymentWebhookEvent;
import com.uyir.hospital.payment.SeatProperties;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.repository.HospitalSeatRepository;
import com.uyir.hospital.repository.SeatPurchaseRepository;
import com.uyir.hospital.service.HospitalSeatService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HospitalSeatServiceImpl implements HospitalSeatService {

    private static final Logger log = LoggerFactory.getLogger(HospitalSeatServiceImpl.class);

    // Appointments that still bring a patient to the doctor; same set the booking service treats as active.
    private static final Set<AppointmentStatus> UPCOMING_STATUSES =
            EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED);

    private final HospitalSeatRepository seatRepository;
    private final SeatPurchaseRepository purchaseRepository;
    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorAppointmentBookingRepository appointmentRepository;
    private final PaymentClient paymentClient;
    private final SeatProperties seatProperties;

    @Override
    public void ensureFreeSeat(String hospitalId) {
        String id = hospitalId + "-free";
        if (seatRepository.existsById(id)) {
            return;
        }
        Instant now = Instant.now();
        insertIgnoringDuplicate(HospitalSeat.builder()
                .id(id)
                .hospitalId(hospitalId)
                .seatType(SeatType.FREE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Override
    public SeatSummaryResponse getSeats(String hospitalId) {
        requireHospital(hospitalId);
        ensureFreeSeat(hospitalId);

        List<HospitalSeat> seats = seatRepository.findByHospitalIdOrderByCreatedAtAsc(hospitalId);
        Map<String, Doctor> doctors = doctorsById(seats);
        int occupied = (int) seats.stream().filter(seat -> seat.getDoctorId() != null).count();

        return SeatSummaryResponse.builder()
                .hospitalId(hospitalId)
                .totalSeats(seats.size())
                .occupiedSeats(occupied)
                .emptySeats(seats.size() - occupied)
                .pricePerSeat(seatProperties.pricePerSeat())
                .currency(seatProperties.currency())
                .seats(seats.stream()
                        .map(seat -> toResponse(seat, seat.getDoctorId() == null ? null : doctors.get(seat.getDoctorId())))
                        .toList())
                .build();
    }

    @Override
    public HospitalSeatResponse assignDoctor(String hospitalId, String seatId, String doctorId) {
        requireHospital(hospitalId);
        ensureFreeSeat(hospitalId);
        HospitalSeat seat = findOwnedSeat(hospitalId, seatId);

        if (doctorId.equals(seat.getDoctorId())) {
            return toResponse(seat, doctorRepository.findById(doctorId).orElse(null));
        }

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id '" + doctorId + "'"));
        if (!doctor.isActive()) {
            throw new IllegalArgumentException("Cannot give a seat to an inactive doctor");
        }
        boolean associated = doctor.getHospitalAssociations() != null
                && doctor.getHospitalAssociations().stream().anyMatch(assoc -> hospitalId.equals(assoc.getHospitalId()));
        if (!associated) {
            throw new IllegalArgumentException(
                    "Doctor '" + doctorId + "' has no hospitalAssociations entry for hospital '" + hospitalId + "'");
        }
        if (seatRepository.existsByHospitalIdAndDoctorId(hospitalId, doctorId)) {
            throw new ConflictException("Doctor '" + doctorId + "' already holds another seat at this hospital");
        }
        if (seat.getDoctorId() != null) {
            ensureNoUpcomingAppointments(hospitalId, seat.getDoctorId());
        }

        Instant now = Instant.now();
        seat.setDoctorId(doctorId);
        seat.setAssignedAt(now);
        seat.setUpdatedAt(now);
        // The partial unique index on (hospitalId, doctorId) catches a concurrent assignment of the
        // same doctor; GlobalExceptionHandler turns that DuplicateKeyException into a 409.
        return toResponse(seatRepository.save(seat), doctor);
    }

    @Override
    public HospitalSeatResponse removeDoctor(String hospitalId, String seatId) {
        requireHospital(hospitalId);
        HospitalSeat seat = findOwnedSeat(hospitalId, seatId);
        if (seat.getDoctorId() == null) {
            return toResponse(seat, null);
        }
        ensureNoUpcomingAppointments(hospitalId, seat.getDoctorId());

        seat.setDoctorId(null);
        seat.setAssignedAt(null);
        seat.setUpdatedAt(Instant.now());
        return toResponse(seatRepository.save(seat), null);
    }

    @Override
    public SeatPurchaseResponse startPurchase(String hospitalId, String payerId, int quantity, String bearerToken) {
        requireHospital(hospitalId);

        Instant now = Instant.now();
        BigDecimal price = seatProperties.pricePerSeat();
        SeatPurchase purchase = purchaseRepository.save(SeatPurchase.builder()
                .hospitalId(hospitalId)
                .quantity(quantity)
                .pricePerSeat(price)
                .amount(price.multiply(BigDecimal.valueOf(quantity)))
                .currency(seatProperties.currency())
                .status(SeatPurchaseStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build());

        return requestPayment(purchase, payerId, bearerToken);
    }

    @Override
    public SeatPurchaseResponse retryPayment(String hospitalId, String payerId, String purchaseId, String bearerToken) {
        SeatPurchase purchase = purchaseRepository.findById(purchaseId)
                .filter(found -> found.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new ResourceNotFoundException("Seat purchase not found with id '" + purchaseId + "'"));
        if (purchase.getStatus() == SeatPurchaseStatus.PAID) {
            return toResponse(purchase);
        }
        return requestPayment(purchase, payerId, bearerToken);
    }

    @Override
    public List<SeatPurchaseResponse> getPurchases(String hospitalId) {
        return purchaseRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void onPaymentEvent(PaymentWebhookEvent event) {
        if (!PaymentClient.REFERENCE_TYPE.equals(event.referenceType())) {
            throw new IllegalArgumentException("referenceType must be " + PaymentClient.REFERENCE_TYPE);
        }
        SeatPurchase purchase = purchaseRepository.findById(event.referenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat purchase not found with id '" + event.referenceId() + "'"));

        switch (event.status()) {
            case PAID -> confirmPaid(purchase, event.paymentId(), event.amount(), event.currency());
            case FAILED -> {
                // A late FAILED after PAID (the hospital retried a failed checkout) changes nothing.
                if (purchase.getStatus() != SeatPurchaseStatus.PAID) {
                    purchase.setStatus(SeatPurchaseStatus.FAILED);
                    purchase.setFailureReason(event.failureReason());
                    purchase.setUpdatedAt(Instant.now());
                    purchaseRepository.save(purchase);
                }
            }
            case PENDING -> log.info("Seat purchase {} payment {} still pending", purchase.getId(), event.paymentId());
        }
    }

    @Override
    public boolean hasSeat(String hospitalId, String doctorId) {
        return seatRepository.existsByHospitalIdAndDoctorId(hospitalId, doctorId);
    }

    @Override
    public Set<String> seatedDoctorIds(String hospitalId) {
        return seatRepository.findByHospitalIdAndDoctorIdIsNotNull(hospitalId).stream()
                .map(HospitalSeat::getDoctorId)
                .collect(Collectors.toSet());
    }

    private SeatPurchaseResponse requestPayment(SeatPurchase purchase, String payerId, String bearerToken) {
        Intent intent = paymentClient.createIntent(purchase, payerId, bearerToken);

        purchase.setPaymentId(intent.id());
        purchase.setCheckoutUrl(intent.checkoutUrl());
        if (purchase.getStatus() == SeatPurchaseStatus.FAILED) {
            purchase.setStatus(SeatPurchaseStatus.PENDING);
            purchase.setFailureReason(null);
        }
        purchase.setUpdatedAt(Instant.now());
        purchase = purchaseRepository.save(purchase);

        if (intent.status() == PaymentStatus.PAID) { // already settled, or the local mock provider
            purchase = confirmPaid(purchase, intent.id(), purchase.getAmount(), purchase.getCurrency());
        }
        return toResponse(purchase);
    }

    // PENDING/FAILED -> PAID and create the seats. Seat ids are "<purchaseId>-<n>", so a duplicate
    // callback (payment-service retries until it gets a 2xx) or a crash halfway through just
    // re-inserts what's missing instead of handing out extra seats.
    private SeatPurchase confirmPaid(SeatPurchase purchase, String paymentId, BigDecimal amount, String currency) {
        if (purchase.getPaymentId() != null && !purchase.getPaymentId().equals(paymentId)) {
            throw new ConflictException("Payment '" + paymentId + "' does not belong to seat purchase '" + purchase.getId() + "'");
        }
        if (!purchase.getCurrency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("currency must be " + purchase.getCurrency());
        }
        if (amount.compareTo(purchase.getAmount()) < 0) {
            log.warn("Seat purchase {}: paid {} but the price is {}", purchase.getId(), amount, purchase.getAmount());
            throw new IllegalArgumentException("Paid amount is less than the seat purchase amount");
        }

        Instant now = Instant.now();
        for (int n = 1; n <= purchase.getQuantity(); n++) {
            insertIgnoringDuplicate(HospitalSeat.builder()
                    .id(purchase.getId() + "-" + n)
                    .hospitalId(purchase.getHospitalId())
                    .seatType(SeatType.PAID)
                    .purchaseId(purchase.getId())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        if (purchase.getStatus() == SeatPurchaseStatus.PAID) {
            return purchase;
        }
        purchase.setStatus(SeatPurchaseStatus.PAID);
        purchase.setPaymentId(paymentId);
        purchase.setCheckoutUrl(null);
        purchase.setFailureReason(null);
        purchase.setPaidAt(now);
        purchase.setUpdatedAt(now);
        log.info("Seat purchase {} paid: {} seat(s) for hospital {}", purchase.getId(), purchase.getQuantity(), purchase.getHospitalId());
        return purchaseRepository.save(purchase);
    }

    private void insertIgnoringDuplicate(HospitalSeat seat) {
        try {
            seatRepository.insert(seat);
        } catch (DuplicateKeyException ignored) {
            // already created by an earlier call
        }
    }

    private void ensureNoUpcomingAppointments(String hospitalId, String doctorId) {
        if (appointmentRepository.existsUpcomingForDoctorAtHospital(hospitalId, doctorId, UPCOMING_STATUSES, Instant.now())) {
            throw new ConflictException("Doctor '" + doctorId + "' still has upcoming appointments at this hospital; "
                    + "cancel or complete them before freeing the seat");
        }
    }

    private void requireHospital(String hospitalId) {
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new ResourceNotFoundException("Hospital not found with id '" + hospitalId + "'");
        }
    }

    private HospitalSeat findOwnedSeat(String hospitalId, String seatId) {
        return seatRepository.findById(seatId)
                .filter(seat -> seat.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id '" + seatId + "'"));
    }

    private Map<String, Doctor> doctorsById(List<HospitalSeat> seats) {
        List<String> ids = seats.stream().map(HospitalSeat::getDoctorId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return doctorRepository.findAllById(ids).stream().collect(Collectors.toMap(Doctor::getId, Function.identity()));
    }

    private HospitalSeatResponse toResponse(HospitalSeat seat, Doctor doctor) {
        return HospitalSeatResponse.builder()
                .id(seat.getId())
                .hospitalId(seat.getHospitalId())
                .seatType(seat.getSeatType())
                .purchaseId(seat.getPurchaseId())
                .doctorId(seat.getDoctorId())
                .doctorName(doctor != null ? doctor.getName() : null)
                .assignedAt(seat.getAssignedAt())
                .createdAt(seat.getCreatedAt())
                .build();
    }

    private SeatPurchaseResponse toResponse(SeatPurchase purchase) {
        return SeatPurchaseResponse.builder()
                .id(purchase.getId())
                .hospitalId(purchase.getHospitalId())
                .quantity(purchase.getQuantity())
                .pricePerSeat(purchase.getPricePerSeat())
                .amount(purchase.getAmount())
                .currency(purchase.getCurrency())
                .status(purchase.getStatus())
                .paymentId(purchase.getPaymentId())
                .checkoutUrl(purchase.getStatus() == SeatPurchaseStatus.PAID ? null : purchase.getCheckoutUrl())
                .failureReason(purchase.getFailureReason())
                .createdAt(purchase.getCreatedAt())
                .paidAt(purchase.getPaidAt())
                .build();
    }
}
