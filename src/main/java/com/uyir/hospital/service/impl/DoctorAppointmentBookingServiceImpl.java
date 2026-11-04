package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.AppointmentDetailResponse;
import com.uyir.hospital.dto.CancelAppointmentRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.DoctorAppointmentDashboardResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.dto.UpdateAppointmentStatusRequest;
import com.uyir.hospital.event.BookingUpdateType;
import com.uyir.hospital.event.BookingUpdatedEvent;
import com.uyir.hospital.event.HospitalEventPublisher;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorAppointmentBookingMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.BookedFor;
import com.uyir.hospital.model.enums.CancelledBy;
import com.uyir.hospital.model.enums.ConsultationType;
import com.uyir.hospital.model.enums.VisitType;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.DoctorAppointmentBookingService;
import com.uyir.hospital.dto.TokenAvailabilityResponse;
import com.uyir.hospital.dto.FollowUpRequest;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.model.embedded.FollowUpInstruction;
import org.bson.types.ObjectId;
import com.uyir.hospital.service.BillingService;
import com.uyir.hospital.service.DoctorTokenLimitService;
import com.uyir.hospital.service.HospitalSeatService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorAppointmentBookingServiceImpl implements DoctorAppointmentBookingService {

    // Statuses that still hold a doctor's slot and can be cancelled, rescheduled or closed out.
    private static final Set<AppointmentStatus> ACTIVE_STATUSES =
            EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED);

    private static final Set<AppointmentStatus> CLOSING_STATUSES =
            EnumSet.of(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

    // Upper bound of DoctorAppointmentBookingRequest.durationMinutes; an existing booking that
    // started earlier than this before a new slot cannot overlap it.
    private static final long MAX_DURATION_SECONDS = 120 * 60L;

    private final DoctorAppointmentBookingRepository doctorAppointmentBookingRepository;
    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorAppointmentBookingMapper doctorAppointmentBookingMapper;
    private final AppointmentTokenGenerator appointmentTokenGenerator;
    private final HospitalSeatService hospitalSeatService;
    private final DoctorTokenLimitService doctorTokenLimitService;
    private final BillingService billingService;
    private final FollowUpStore followUpStore;
    private final HospitalEventPublisher hospitalEventPublisher;

    @Override
    public DoctorAppointmentBookingResponse create(String patientId, DoctorAppointmentBookingRequest request) {
        if (!hospitalRepository.existsById(request.getHospitalId())) {
            throw new ResourceNotFoundException("Hospital not found with id '" + request.getHospitalId() + "'");
        }
        // An unpaid commission invoice past its due date pauses new appointments (emergencies are unaffected).
        if (billingService.hasOverdueInvoice(request.getHospitalId())) {
            throw new ConflictException("Hospital '" + request.getHospitalId()
                    + "' is not accepting new appointments at the moment");
        }

        Doctor doctor = doctorRepository
                .findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id '" + request.getDoctorId() + "'"));

        if (!doctor.isActive()) {
            throw new IllegalArgumentException("Cannot book an appointment with an inactive doctor");
        }

        List<HospitalAssociation> associations = doctor.getHospitalAssociations();
        boolean associated = associations != null
                && associations.stream().anyMatch(assoc -> request.getHospitalId().equals(assoc.getHospitalId()));
        if (!associated) {
            throw new IllegalArgumentException(
                    "Doctor '" + request.getDoctorId() + "' is not associated with hospital '" + request.getHospitalId() + "'");
        }
        // Only the doctors a hospital has put in a seat take appointments there.
        if (!hospitalSeatService.hasSeat(request.getHospitalId(), request.getDoctorId())) {
            throw new IllegalArgumentException(
                    "Doctor '" + request.getDoctorId() + "' does not hold a seat at hospital '" + request.getHospitalId() + "'");
        }

        BookedFor bookedFor = request.getBookedFor() != null ? request.getBookedFor() : BookedFor.SELF;
        validateBookedFor(bookedFor, request);

        VisitType visitType = request.getVisitType() != null ? request.getVisitType() : VisitType.NEW;
        validateVisitType(visitType, patientId, request);

        int durationMinutes = request.getDurationMinutes() != null
                ? request.getDurationMinutes()
                : DoctorAppointmentBooking.DEFAULT_DURATION_MINUTES;
        ensureSlotFree(request.getDoctorId(), request.getAppointmentDateTime(), durationMinutes, null);

        // The id is chosen up front so a follow-up can be linked to the visit that asked for it before saving.
        String bookingId = new ObjectId().toHexString();
        String previousId = visitType == VisitType.FOLLOW_UP ? request.getPreviousAppointmentId() : null;
        if (previousId != null && !followUpStore.link(previousId, bookingId)) {
            throw new ConflictException("A follow-up for appointment '" + previousId + "' is already booked");
        }

        // Last check before saving: 409 when the hospital's tokens for that doctor and day are all taken.
        int tokenNumber;
        try {
            tokenNumber = claimToken(request.getHospitalId(), request.getDoctorId(), request.getAppointmentDateTime());
        } catch (RuntimeException noToken) {
            if (previousId != null) {
                followUpStore.unlink(previousId, bookingId);
            }
            throw noToken;
        }

        Instant now = Instant.now();
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id(bookingId)
                .patientId(patientId)
                .patientName(request.getPatientName())
                .patientPhone(request.getPatientPhone())
                .patientEmail(request.getPatientEmail())
                .patientAge(request.getPatientAge())
                .patientGender(request.getPatientGender())
                .bookedFor(bookedFor)
                .relationship(request.getRelationship())
                .hospitalId(request.getHospitalId())
                .doctorId(request.getDoctorId())
                .appointmentDateTime(request.getAppointmentDateTime())
                .durationMinutes(durationMinutes)
                .tokenNumber(tokenNumber)
                .consultationType(request.getConsultationType() != null
                        ? request.getConsultationType()
                        : ConsultationType.IN_PERSON)
                .visitType(visitType)
                .previousAppointmentId(request.getPreviousAppointmentId())
                .reason(request.getReason())
                .symptoms(request.getSymptoms())
                .status(AppointmentStatus.CONFIRMED)
                .commissionFee(billingService.currentCommissionPerAppointment())
                .createdAt(now)
                .updatedAt(now)
                .build();

        DoctorAppointmentBooking saved;
        try {
            saved = doctorAppointmentBookingRepository.save(booking);
        } catch (RuntimeException saveFailed) {
            appointmentTokenGenerator.release(
                    request.getHospitalId(), request.getDoctorId(), request.getAppointmentDateTime(), tokenNumber);
            if (previousId != null) {
                followUpStore.unlink(previousId, bookingId);
            }
            throw saveFailed;
        }
        publishUpdate(BookingUpdateType.APPOINTMENT_BOOKED, saved, Role.PATIENT);
        return doctorAppointmentBookingMapper.toResponse(saved);
    }

    // Live WebSocket update for the hospital and the patient. Best-effort: the publisher never throws.
    private void publishUpdate(BookingUpdateType type, DoctorAppointmentBooking booking, Role changedBy) {
        hospitalEventPublisher.bookingUpdated(BookingUpdatedEvent.of(type, booking, changedBy));
    }

    @Override
    public DoctorAppointmentBookingResponse setFollowUp(String id, String hospitalId, FollowUpRequest request) {
        DoctorAppointmentBooking booking = findOwnedByHospital(id, hospitalId);
        if (booking.getStatus() != AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("A follow-up can only be set on a COMPLETED appointment");
        }
        FollowUpInstruction followUp = buildFollowUp(booking, request, hospitalId);
        if (!followUpStore.replaceIfNotBooked(id, followUp)) {
            throw followUpAlreadyBooked(id);
        }
        return doctorAppointmentBookingMapper.toResponse(findById(id));
    }

    @Override
    public DoctorAppointmentBookingResponse removeFollowUp(String id, String hospitalId) {
        DoctorAppointmentBooking booking = findOwnedByHospital(id, hospitalId);
        if (booking.getFollowUp() == null) {
            throw new ResourceNotFoundException("Appointment '" + id + "' has no follow-up instruction");
        }
        if (!followUpStore.replaceIfNotBooked(id, null)) {
            throw followUpAlreadyBooked(id);
        }
        return doctorAppointmentBookingMapper.toResponse(findById(id));
    }

    // Due date = the given date, or the visit's (IST) date + afterDays. Must fall after the visit and not in the past.
    private static FollowUpInstruction buildFollowUp(
            DoctorAppointmentBooking booking, FollowUpRequest request, String hospitalId) {
        if ((request.getAfterDays() == null) == (request.getDate() == null)) {
            throw new IllegalArgumentException("Give exactly one of followUp.afterDays or followUp.date");
        }
        LocalDate visitDay = AppointmentTokenGenerator.localDay(booking.getAppointmentDateTime());
        LocalDate due = request.getDate() != null ? request.getDate() : visitDay.plusDays(request.getAfterDays());
        if (!due.isAfter(visitDay)) {
            throw new IllegalArgumentException("The follow-up date must be after the visit (" + visitDay + ")");
        }
        if (due.isBefore(LocalDate.now(AppointmentTokenGenerator.ZONE))) {
            throw new IllegalArgumentException("The follow-up date " + due + " is in the past");
        }
        String instructions = request.getInstructions() == null || request.getInstructions().isBlank()
                ? null
                : request.getInstructions().trim();
        return FollowUpInstruction.builder()
                .dueDate(due.toString())
                .instructions(instructions)
                .setBy(hospitalId)
                .setAt(Instant.now())
                .build();
    }

    private static ConflictException followUpAlreadyBooked(String id) {
        return new ConflictException("The patient has already booked the follow-up for appointment '" + id
                + "'; it can no longer be changed");
    }

    @Override
    public TokenAvailabilityResponse getAvailability(String hospitalId, String doctorId, LocalDate date) {
        Integer limit = doctorTokenLimitService.limitFor(hospitalId, doctorId, date);
        int booked = appointmentTokenGenerator.takenTokens(hospitalId, doctorId, date).size();
        Integer remaining = limit == null ? null : Math.max(0, limit - booked);
        boolean bookingOpen = !billingService.hasOverdueInvoice(hospitalId);
        return TokenAvailabilityResponse.builder()
                .hospitalId(hospitalId)
                .doctorId(doctorId)
                .date(date)
                .tokenLimit(limit)
                .bookedTokens(booked)
                .remainingTokens(remaining)
                .available(bookingOpen && (remaining == null || remaining > 0))
                .build();
    }

    @Override
    public List<DoctorAppointmentBookingResponse> getByHospitalId(String hospitalId) {
        return doctorAppointmentBookingRepository.findByHospitalIdOrderByAppointmentDateTimeAsc(hospitalId).stream()
                .map(doctorAppointmentBookingMapper::toResponse)
                .toList();
    }

    @Override
    public List<DoctorAppointmentBookingResponse> getByPatientId(String patientId) {
        return doctorAppointmentBookingRepository.findByPatientIdOrderByAppointmentDateTimeDesc(patientId).stream()
                .map(doctorAppointmentBookingMapper::toResponse)
                .toList();
    }

    @Override
    public DoctorAppointmentDashboardResponse getDoctorDashboard(
            String hospitalId, String doctorId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' (" + from + ") must not be after 'to' (" + to + ")");
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id '" + doctorId + "'"));
        // Kept even if the association is now inactive, so a hospital can still see a former doctor's history.
        HospitalAssociation association = doctor.getHospitalAssociations() == null ? null
                : doctor.getHospitalAssociations().stream()
                        .filter(a -> hospitalId.equals(a.getHospitalId()))
                        .findFirst()
                        .orElse(null);
        if (association == null) {
            throw new ForbiddenException("Doctor '" + doctorId + "' is not associated with hospital '" + hospitalId + "'");
        }

        List<DoctorAppointmentBooking> bookings;
        if (from == null && to == null) {
            bookings = doctorAppointmentBookingRepository
                    .findByHospitalIdAndDoctorIdOrderByAppointmentDateTimeAsc(hospitalId, doctorId);
        } else {
            // Open-ended sides get a far bound; IST days, half-open [from 00:00, to+1 00:00).
            Instant start = from != null ? from.atStartOfDay(AppointmentTokenGenerator.ZONE).toInstant() : Instant.EPOCH;
            Instant end = to != null ? to.plusDays(1).atStartOfDay(AppointmentTokenGenerator.ZONE).toInstant()
                    : Instant.parse("9999-12-31T00:00:00Z");
            bookings = doctorAppointmentBookingRepository
                    .findByHospitalIdAndDoctorIdScheduledBetween(hospitalId, doctorId, start, end);
        }

        long completed = countStatus(bookings, EnumSet.of(AppointmentStatus.COMPLETED));
        long remaining = countStatus(bookings, ACTIVE_STATUSES);
        long noShow = countStatus(bookings, EnumSet.of(AppointmentStatus.NO_SHOW));
        long cancelled = countStatus(bookings, EnumSet.of(AppointmentStatus.CANCELLED));

        DoctorAppointmentDashboardResponse.DoctorInfo doctorInfo = DoctorAppointmentDashboardResponse.DoctorInfo.builder()
                .id(doctor.getId())
                .name(doctor.getName())
                .qualifications(doctor.getQualifications())
                .specialties(doctor.getSpecialties())
                .yearsOfExperience(doctor.getYearsOfExperience())
                .department(association.getDepartment())
                .consultationFee(association.getConsultationFee() != null
                        ? association.getConsultationFee() : doctor.getConsultationFee())
                .availability(doctor.getAvailability())
                .contactDetails(doctor.getContactDetails())
                .seated(hospitalSeatService.hasSeat(hospitalId, doctorId))
                .checkedIn(hospitalId.equals(doctor.getCurrentHospitalId()))
                .checkedInAt(hospitalId.equals(doctor.getCurrentHospitalId()) ? doctor.getCheckedInAt() : null)
                .active(doctor.isActive() && association.isActive())
                .build();

        return DoctorAppointmentDashboardResponse.builder()
                .doctor(doctorInfo)
                .from(from)
                .to(to)
                .summary(DoctorAppointmentDashboardResponse.Summary.builder()
                        .totalAppointments(bookings.size())
                        .completed(completed)
                        .remaining(remaining)
                        .noShow(noShow)
                        .cancelled(cancelled)
                        .build())
                .appointments(bookings.stream().map(doctorAppointmentBookingMapper::toResponse).toList())
                .build();
    }

    private static long countStatus(List<DoctorAppointmentBooking> bookings, Set<AppointmentStatus> statuses) {
        return bookings.stream().filter(b -> statuses.contains(b.getStatus())).count();
    }

    @Override
    public AppointmentDetailResponse getDetailForPatient(String id, String patientId) {
        DoctorAppointmentBooking booking = findById(id);
        if (!booking.getPatientId().equals(patientId)) {
            throw new ForbiddenException("Appointment booking '" + id + "' does not belong to patient '" + patientId + "'");
        }

        AppointmentDetailResponse.HospitalInfo hospital = hospitalRepository.findById(booking.getHospitalId())
                .map(h -> AppointmentDetailResponse.HospitalInfo.builder()
                        .id(h.getId())
                        .hospitalName(h.getHospitalName())
                        .address(h.getAddress())
                        .contactDetails(h.getContactDetails())
                        .build())
                .orElse(null);
        AppointmentDetailResponse.DoctorInfo doctor = doctorRepository.findById(booking.getDoctorId())
                .map(d -> AppointmentDetailResponse.DoctorInfo.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .qualifications(d.getQualifications())
                        .specialties(d.getSpecialties())
                        .yearsOfExperience(d.getYearsOfExperience())
                        .consultationFee(d.getConsultationFee())
                        .build())
                .orElse(null);

        return AppointmentDetailResponse.builder()
                .appointment(doctorAppointmentBookingMapper.toResponse(booking))
                .hospital(hospital)
                .doctor(doctor)
                .cancellable(ACTIVE_STATUSES.contains(booking.getStatus()))
                .build();
    }

    @Override
    public DoctorAppointmentBookingResponse reschedule(String id, String hospitalId, RescheduleAppointmentRequest request) {
        DoctorAppointmentBooking booking = findOwnedByHospital(id, hospitalId);

        if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
            throw new IllegalArgumentException("Cannot reschedule an appointment with status " + booking.getStatus());
        }

        ensureSlotFree(booking.getDoctorId(), request.getAppointmentDateTime(), booking.effectiveDurationMinutes(), id);

        // A token belongs to one day's queue. Moving to another day takes a token on that day (409 if
        // they are all booked) and frees the old one for someone else.
        if (booking.getAppointmentDateTime() == null
                || !AppointmentTokenGenerator.sameDay(booking.getAppointmentDateTime(), request.getAppointmentDateTime())) {
            int newToken = claimToken(booking.getHospitalId(), booking.getDoctorId(), request.getAppointmentDateTime());
            appointmentTokenGenerator.release(booking.getHospitalId(), booking.getDoctorId(),
                    booking.getAppointmentDateTime(), booking.getTokenNumber());
            booking.setTokenNumber(newToken);
        }

        booking.setAppointmentDateTime(request.getAppointmentDateTime());
        booking.setStatus(AppointmentStatus.RESCHEDULED);
        booking.setUpdatedAt(Instant.now());

        DoctorAppointmentBooking saved = doctorAppointmentBookingRepository.save(booking);
        publishUpdate(BookingUpdateType.APPOINTMENT_RESCHEDULED, saved, Role.HOSPITAL);
        return doctorAppointmentBookingMapper.toResponse(saved);
    }

    @Override
    public DoctorAppointmentBookingResponse cancel(String id, String userId, Role role, CancelAppointmentRequest request) {
        DoctorAppointmentBooking booking = findById(id);

        CancelledBy cancelledBy;
        if (role == Role.PATIENT) {
            if (!booking.getPatientId().equals(userId)) {
                throw new ForbiddenException("Appointment booking '" + id + "' does not belong to patient '" + userId + "'");
            }
            cancelledBy = CancelledBy.PATIENT;
        } else if (role == Role.HOSPITAL) {
            if (!booking.getHospitalId().equals(userId)) {
                throw new ForbiddenException("Appointment booking '" + id + "' does not belong to hospital '" + userId + "'");
            }
            cancelledBy = CancelledBy.HOSPITAL;
        } else {
            throw new ForbiddenException("Only the patient or the hospital can cancel an appointment");
        }

        if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel an appointment with status " + booking.getStatus());
        }

        Instant now = Instant.now();
        booking.setStatus(AppointmentStatus.CANCELLED);
        booking.setCancellationReason(request.getReason());
        booking.setCancelledBy(cancelledBy);
        booking.setCancelledAt(now);
        booking.setUpdatedAt(now);

        DoctorAppointmentBooking saved = doctorAppointmentBookingRepository.save(booking);
        // The cancelled token goes back to the pool; the next patient booking that day gets it.
        appointmentTokenGenerator.release(
                booking.getHospitalId(), booking.getDoctorId(), booking.getAppointmentDateTime(), booking.getTokenNumber());
        // A cancelled follow-up can be booked again.
        unlinkFollowUp(booking);
        publishUpdate(BookingUpdateType.APPOINTMENT_CANCELLED, saved, role);
        return doctorAppointmentBookingMapper.toResponse(saved);
    }

    private int claimToken(String hospitalId, String doctorId, Instant appointmentDateTime) {
        Integer limit = doctorTokenLimitService.limitFor(
                hospitalId, doctorId, AppointmentTokenGenerator.localDay(appointmentDateTime));
        return appointmentTokenGenerator.claim(hospitalId, doctorId, appointmentDateTime, limit);
    }

    @Override
    public DoctorAppointmentBookingResponse updateStatus(
            String id, String hospitalId, UpdateAppointmentStatusRequest request) {
        if (!CLOSING_STATUSES.contains(request.getStatus())) {
            throw new IllegalArgumentException("Status can only be set to " + CLOSING_STATUSES
                    + "; use the cancel or reschedule endpoints for other changes");
        }

        DoctorAppointmentBooking booking = findOwnedByHospital(id, hospitalId);

        if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
            throw new IllegalArgumentException("Cannot change the status of an appointment with status " + booking.getStatus());
        }

        Instant now = Instant.now();
        if (booking.getAppointmentDateTime().isAfter(now)) {
            throw new IllegalArgumentException("Cannot mark an appointment " + request.getStatus() + " before its scheduled time");
        }
        if (request.getFollowUp() != null) {
            if (request.getStatus() != AppointmentStatus.COMPLETED) {
                throw new IllegalArgumentException("followUp can only be given when marking an appointment COMPLETED");
            }
            booking.setFollowUp(buildFollowUp(booking, request.getFollowUp(), hospitalId));
        }

        booking.setStatus(request.getStatus());
        booking.setUpdatedAt(now);

        DoctorAppointmentBooking saved = doctorAppointmentBookingRepository.save(booking);
        // A missed follow-up visit frees the instruction so the patient can book it again within the window.
        if (request.getStatus() == AppointmentStatus.NO_SHOW) {
            unlinkFollowUp(booking);
        }
        publishUpdate(request.getStatus() == AppointmentStatus.COMPLETED
                ? BookingUpdateType.APPOINTMENT_COMPLETED : BookingUpdateType.APPOINTMENT_NO_SHOW, saved, Role.HOSPITAL);
        return doctorAppointmentBookingMapper.toResponse(saved);
    }

    private void unlinkFollowUp(DoctorAppointmentBooking booking) {
        if (booking.getVisitType() == VisitType.FOLLOW_UP && booking.getPreviousAppointmentId() != null) {
            followUpStore.unlink(booking.getPreviousAppointmentId(), booking.getId());
        }
    }

    private DoctorAppointmentBooking findById(String id) {
        return doctorAppointmentBookingRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment booking not found with id '" + id + "'"));
    }

    private DoctorAppointmentBooking findOwnedByHospital(String id, String hospitalId) {
        DoctorAppointmentBooking booking = findById(id);
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new ForbiddenException("Appointment booking '" + id + "' does not belong to hospital '" + hospitalId + "'");
        }
        return booking;
    }

    private void validateBookedFor(BookedFor bookedFor, DoctorAppointmentBookingRequest request) {
        if (bookedFor == BookedFor.FAMILY_MEMBER && request.getRelationship() == null) {
            throw new IllegalArgumentException("relationship is required when booking for a family member");
        }
        if (bookedFor == BookedFor.SELF && request.getRelationship() != null) {
            throw new IllegalArgumentException("relationship must be omitted when booking for self");
        }
    }

    private void validateVisitType(VisitType visitType, String patientId, DoctorAppointmentBookingRequest request) {
        String previousId = request.getPreviousAppointmentId();
        if (visitType == VisitType.NEW) {
            if (previousId != null) {
                throw new IllegalArgumentException("previousAppointmentId is only allowed for FOLLOW_UP visits");
            }
            return;
        }

        if (previousId == null || previousId.isBlank()) {
            throw new IllegalArgumentException("previousAppointmentId is required for FOLLOW_UP visits");
        }
        DoctorAppointmentBooking previous = doctorAppointmentBookingRepository
                .findById(previousId)
                .orElseThrow(() -> new ResourceNotFoundException("Previous appointment not found with id '" + previousId + "'"));
        if (!previous.getPatientId().equals(patientId)) {
            throw new ForbiddenException("Previous appointment '" + previousId + "' does not belong to patient '" + patientId + "'");
        }
        if (previous.getStatus() != AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("A follow-up must reference a COMPLETED appointment");
        }
        FollowUpInstruction followUp = previous.getFollowUp();
        if (followUp == null || followUp.getDueDate() == null) {
            throw new IllegalArgumentException("The hospital has not asked for a follow-up on appointment '" + previousId + "'");
        }
        if (!previous.getHospitalId().equals(request.getHospitalId()) || !previous.getDoctorId().equals(request.getDoctorId())) {
            throw new IllegalArgumentException("A follow-up must be with the same doctor at the same hospital as appointment '"
                    + previousId + "' (doctor '" + previous.getDoctorId() + "', hospital '" + previous.getHospitalId() + "')");
        }
        LocalDate due = LocalDate.parse(followUp.getDueDate());
        LocalDate from = FollowUpInstruction.bookableFrom(due);
        LocalDate until = FollowUpInstruction.bookableUntil(due);
        LocalDate day = AppointmentTokenGenerator.localDay(request.getAppointmentDateTime());
        if (day.isBefore(from) || day.isAfter(until)) {
            throw new IllegalArgumentException("This follow-up is due on " + due + " and can be booked for a date from "
                    + from + " to " + until);
        }
        if (followUp.getFollowUpBookingId() != null) {
            throw new ConflictException("A follow-up for appointment '" + previousId + "' is already booked");
        }
    }

    // Not atomic: two concurrent requests for the same slot can both pass this check.
    private void ensureSlotFree(String doctorId, Instant start, int durationMinutes, String excludeBookingId) {
        Instant end = start.plusSeconds(durationMinutes * 60L);
        boolean clash = doctorAppointmentBookingRepository
                .findByDoctorIdStatusInStartingBetween(doctorId, ACTIVE_STATUSES, start.minusSeconds(MAX_DURATION_SECONDS), end)
                .stream()
                .filter(existing -> !existing.getId().equals(excludeBookingId))
                .anyMatch(existing -> existing.appointmentEndTime().isAfter(start));
        if (clash) {
            throw new DuplicateResourceException("Doctor '" + doctorId + "' already has an appointment between "
                    + start + " and " + end);
        }
    }
}
