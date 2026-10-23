package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.HospitalSeatResponse;
import com.uyir.hospital.dto.SeatPurchaseResponse;
import com.uyir.hospital.dto.SeatSummaryResponse;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.HospitalSeat;
import com.uyir.hospital.model.SeatPurchase;
import com.uyir.hospital.model.embedded.HospitalAssociation;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class HospitalSeatServiceImplTest {

    private static final BigDecimal PRICE = new BigDecimal("10000.00");

    @Mock
    private HospitalSeatRepository seatRepository;

    @Mock
    private SeatPurchaseRepository purchaseRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorAppointmentBookingRepository appointmentRepository;

    @Mock
    private PaymentClient paymentClient;

    private HospitalSeatServiceImpl service;

    @BeforeEach
    void setUp() {
        SeatProperties props = new SeatProperties(PRICE, "INR", new SeatProperties.Payment(
                "http", "http://localhost:8083", "secret", Duration.ofMinutes(5), Duration.ofSeconds(3), Duration.ofSeconds(10)));
        service = new HospitalSeatServiceImpl(seatRepository, purchaseRepository, hospitalRepository, doctorRepository,
                appointmentRepository, paymentClient, props);
        lenient().when(hospitalRepository.existsById("h1")).thenReturn(true);
        lenient().when(seatRepository.existsById("h1-free")).thenReturn(true);
        lenient().when(seatRepository.save(any(HospitalSeat.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(purchaseRepository.save(any(SeatPurchase.class))).thenAnswer(inv -> {
            SeatPurchase purchase = inv.getArgument(0);
            if (purchase.getId() == null) {
                purchase.setId("p1");
            }
            return purchase;
        });
    }

    private Doctor doctorAt(String id, String hospitalId) {
        return Doctor.builder()
                .id(id)
                .name("Dr " + id)
                .active(true)
                .hospitalAssociations(List.of(HospitalAssociation.builder().hospitalId(hospitalId).build()))
                .build();
    }

    private HospitalSeat seat(String id, String doctorId) {
        return HospitalSeat.builder().id(id).hospitalId("h1").seatType(SeatType.PAID).doctorId(doctorId).build();
    }

    private SeatPurchase pendingPurchase(int quantity) {
        return SeatPurchase.builder()
                .id("p1")
                .hospitalId("h1")
                .quantity(quantity)
                .pricePerSeat(PRICE)
                .amount(PRICE.multiply(BigDecimal.valueOf(quantity)))
                .currency("INR")
                .status(SeatPurchaseStatus.PENDING)
                .paymentId("pay1")
                .build();
    }

    private PaymentWebhookEvent event(PaymentStatus status, String amount) {
        return new PaymentWebhookEvent("pay1", "HOSPITAL_SEAT", "p1", status, new BigDecimal(amount), "INR", null);
    }

    @Test
    void ensureFreeSeat_missing_insertsFreeSeat() {
        when(seatRepository.existsById("h2-free")).thenReturn(false);

        service.ensureFreeSeat("h2");

        ArgumentCaptor<HospitalSeat> captor = ArgumentCaptor.forClass(HospitalSeat.class);
        verify(seatRepository).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("h2-free");
        assertThat(captor.getValue().getSeatType()).isEqualTo(SeatType.FREE);
    }

    @Test
    void ensureFreeSeat_alreadyThere_insertsNothing() {
        service.ensureFreeSeat("h1");

        verify(seatRepository, never()).insert(any(HospitalSeat.class));
    }

    @Test
    void getSeats_onlyEmptySeats_returnsThemWithoutDoctors() {
        when(seatRepository.findByHospitalIdOrderByCreatedAtAsc("h1")).thenReturn(List.of(seat("h1-free", null)));

        SeatSummaryResponse summary = service.getSeats("h1");

        assertThat(summary.getTotalSeats()).isEqualTo(1);
        assertThat(summary.getEmptySeats()).isEqualTo(1);
        assertThat(summary.getSeats().get(0).getDoctorId()).isNull();
    }

    @Test
    void assignDoctor_emptySeat_seatsDoctor() {
        when(seatRepository.findById("s1")).thenReturn(Optional.of(seat("s1", null)));
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctorAt("d1", "h1")));

        HospitalSeatResponse response = service.assignDoctor("h1", "s1", "d1");

        assertThat(response.getDoctorId()).isEqualTo("d1");
        assertThat(response.getDoctorName()).isEqualTo("Dr d1");
        assertThat(response.getAssignedAt()).isNotNull();
    }

    @Test
    void assignDoctor_swap_whenOutgoingDoctorHasNoUpcomingAppointments_replacesDoctor() {
        when(seatRepository.findById("s1")).thenReturn(Optional.of(seat("s1", "old")));
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctorAt("d1", "h1")));
        when(appointmentRepository.existsUpcomingForDoctorAtHospital(eq("h1"), eq("old"), anyCollection(), any()))
                .thenReturn(false);

        HospitalSeatResponse response = service.assignDoctor("h1", "s1", "d1");

        assertThat(response.getDoctorId()).isEqualTo("d1");
    }

    @Test
    void assignDoctor_swap_whenOutgoingDoctorHasUpcomingAppointments_throwsConflict() {
        when(seatRepository.findById("s1")).thenReturn(Optional.of(seat("s1", "old")));
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctorAt("d1", "h1")));
        when(appointmentRepository.existsUpcomingForDoctorAtHospital(eq("h1"), eq("old"), anyCollection(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.assignDoctor("h1", "s1", "d1")).isInstanceOf(ConflictException.class);
        verify(seatRepository, never()).save(any());
    }

    @Test
    void assignDoctor_doctorAlreadyInAnotherSeat_throwsConflict() {
        when(seatRepository.findById("s1")).thenReturn(Optional.of(seat("s1", null)));
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctorAt("d1", "h1")));
        when(seatRepository.existsByHospitalIdAndDoctorId("h1", "d1")).thenReturn(true);

        assertThatThrownBy(() -> service.assignDoctor("h1", "s1", "d1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void assignDoctor_doctorNotAssociatedWithHospital_throwsIllegalArgument() {
        when(seatRepository.findById("s1")).thenReturn(Optional.of(seat("s1", null)));
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctorAt("d1", "other")));

        assertThatThrownBy(() -> service.assignDoctor("h1", "s1", "d1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignDoctor_seatOfAnotherHospital_throwsNotFound() {
        HospitalSeat foreign = seat("s1", null);
        foreign.setHospitalId("h2");
        when(seatRepository.findById("s1")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.assignDoctor("h1", "s1", "d1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeDoctor_withUpcomingAppointments_throwsConflict() {
        when(seatRepository.findById("s1")).thenReturn(Optional.of(seat("s1", "d1")));
        when(appointmentRepository.existsUpcomingForDoctorAtHospital(eq("h1"), eq("d1"), anyCollection(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.removeDoctor("h1", "s1")).isInstanceOf(ConflictException.class);
    }

    @Test
    void removeDoctor_noUpcomingAppointments_emptiesSeat() {
        when(seatRepository.findById("s1")).thenReturn(Optional.of(seat("s1", "d1")));

        HospitalSeatResponse response = service.removeDoctor("h1", "s1");

        assertThat(response.getDoctorId()).isNull();
    }

    @Test
    void startPurchase_chargesPricePerSeatAndReturnsCheckout() {
        when(paymentClient.createIntent(any(), eq("u1"), eq("token")))
                .thenReturn(new Intent("pay1", PaymentStatus.PENDING, "https://rzp.io/i/x"));

        SeatPurchaseResponse response = service.startPurchase("h1", "u1", 3, "token");

        assertThat(response.getAmount()).isEqualByComparingTo("30000");
        assertThat(response.getStatus()).isEqualTo(SeatPurchaseStatus.PENDING);
        assertThat(response.getCheckoutUrl()).isEqualTo("https://rzp.io/i/x");
        verify(seatRepository, never()).insert(any(HospitalSeat.class));
    }

    @Test
    void startPurchase_alreadyPaid_createsSeatsAtOnce() {
        when(paymentClient.createIntent(any(), eq("u1"), anyString()))
                .thenReturn(new Intent("mock-p1", PaymentStatus.PAID, null));

        SeatPurchaseResponse response = service.startPurchase("h1", "u1", 2, "token");

        assertThat(response.getStatus()).isEqualTo(SeatPurchaseStatus.PAID);
        verify(seatRepository, times(2)).insert(any(HospitalSeat.class));
    }

    @Test
    void onPaymentEvent_paid_createsOneSeatPerQuantityAndMarksPaid() {
        when(purchaseRepository.findById("p1")).thenReturn(Optional.of(pendingPurchase(2)));

        service.onPaymentEvent(event(PaymentStatus.PAID, "20000.00"));

        ArgumentCaptor<HospitalSeat> seats = ArgumentCaptor.forClass(HospitalSeat.class);
        verify(seatRepository, times(2)).insert(seats.capture());
        assertThat(seats.getAllValues()).extracting(HospitalSeat::getId).containsExactly("p1-1", "p1-2");
        ArgumentCaptor<SeatPurchase> purchase = ArgumentCaptor.forClass(SeatPurchase.class);
        verify(purchaseRepository).save(purchase.capture());
        assertThat(purchase.getValue().getStatus()).isEqualTo(SeatPurchaseStatus.PAID);
    }

    @Test
    void onPaymentEvent_duplicatePaid_addsNoExtraSeats() {
        SeatPurchase paid = pendingPurchase(1);
        paid.setStatus(SeatPurchaseStatus.PAID);
        when(purchaseRepository.findById("p1")).thenReturn(Optional.of(paid));
        when(seatRepository.insert(any(HospitalSeat.class))).thenThrow(new DuplicateKeyException("dup"));

        service.onPaymentEvent(event(PaymentStatus.PAID, "10000.00"));

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void onPaymentEvent_amountTooLow_throwsAndCreatesNoSeats() {
        when(purchaseRepository.findById("p1")).thenReturn(Optional.of(pendingPurchase(2)));

        assertThatThrownBy(() -> service.onPaymentEvent(event(PaymentStatus.PAID, "10000.00")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(seatRepository, never()).insert(any(HospitalSeat.class));
    }

    @Test
    void onPaymentEvent_lateFailedAfterPaid_changesNothing() {
        SeatPurchase paid = pendingPurchase(1);
        paid.setStatus(SeatPurchaseStatus.PAID);
        when(purchaseRepository.findById("p1")).thenReturn(Optional.of(paid));

        service.onPaymentEvent(event(PaymentStatus.FAILED, "10000.00"));

        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void onPaymentEvent_wrongReferenceType_throwsIllegalArgument() {
        PaymentWebhookEvent other = new PaymentWebhookEvent(
                "pay1", "AMBULANCE_BOOKING", "p1", PaymentStatus.PAID, PRICE, "INR", null);

        assertThatThrownBy(() -> service.onPaymentEvent(other)).isInstanceOf(IllegalArgumentException.class);
    }
}
