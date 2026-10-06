package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.CancelAppointmentRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.dto.UpdateAppointmentStatusRequest;
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
import java.time.Instant;
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

    @Override
    public DoctorAppointmentBookingResponse create(String patientId, DoctorAppointmentBookingRequest request) {
        if (!hospitalRepository.existsById(request.getHospitalId())) {
            throw new ResourceNotFoundException("Hospital not found with id '" + request.getHospitalId() + "'");
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

        BookedFor bookedFor = request.getBookedFor() != null ? request.getBookedFor() : BookedFor.SELF;
        validateBookedFor(bookedFor, request);

        VisitType visitType = request.getVisitType() != null ? request.getVisitType() : VisitType.NEW;
        validateVisitType(visitType, patientId, request);

        int durationMinutes = request.getDurationMinutes() != null
                ? request.getDurationMinutes()
                : DoctorAppointmentBooking.DEFAULT_DURATION_MINUTES;
        ensureSlotFree(request.getDoctorId(), request.getAppointmentDateTime(), durationMinutes, null);

        Instant now = Instant.now();
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .patientId(patientId)
                .patientName(request.getPatientName())
                .patientPhone(request.getPatientPhone())
                .patientAge(request.getPatientAge())
                .patientGender(request.getPatientGender())
                .bookedFor(bookedFor)
                .relationship(request.getRelationship())
                .hospitalId(request.getHospitalId())
                .doctorId(request.getDoctorId())
                .appointmentDateTime(request.getAppointmentDateTime())
                .durationMinutes(durationMinutes)
                .tokenNumber(appointmentTokenGenerator.nextToken(request.getDoctorId(), request.getAppointmentDateTime()))
                .consultationType(request.getConsultationType() != null
                        ? request.getConsultationType()
                        : ConsultationType.IN_PERSON)
                .visitType(visitType)
                .previousAppointmentId(request.getPreviousAppointmentId())
                .reason(request.getReason())
                .symptoms(request.getSymptoms())
                .status(AppointmentStatus.CONFIRMED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return doctorAppointmentBookingMapper.toResponse(doctorAppointmentBookingRepository.save(booking));
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
    public DoctorAppointmentBookingResponse reschedule(String id, String hospitalId, RescheduleAppointmentRequest request) {
        DoctorAppointmentBooking booking = findOwnedByHospital(id, hospitalId);

        if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
            throw new IllegalArgumentException("Cannot reschedule an appointment with status " + booking.getStatus());
        }

        ensureSlotFree(booking.getDoctorId(), request.getAppointmentDateTime(), booking.effectiveDurationMinutes(), id);

        // A token belongs to one day's queue; moving to another day joins the end of that day's queue.
        if (booking.getAppointmentDateTime() == null
                || !AppointmentTokenGenerator.sameDay(booking.getAppointmentDateTime(), request.getAppointmentDateTime())) {
            booking.setTokenNumber(
                    appointmentTokenGenerator.nextToken(booking.getDoctorId(), request.getAppointmentDateTime()));
        }

        booking.setAppointmentDateTime(request.getAppointmentDateTime());
        booking.setStatus(AppointmentStatus.RESCHEDULED);
        booking.setUpdatedAt(Instant.now());

        return doctorAppointmentBookingMapper.toResponse(doctorAppointmentBookingRepository.save(booking));
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

        return doctorAppointmentBookingMapper.toResponse(doctorAppointmentBookingRepository.save(booking));
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

        booking.setStatus(request.getStatus());
        booking.setUpdatedAt(now);

        return doctorAppointmentBookingMapper.toResponse(doctorAppointmentBookingRepository.save(booking));
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
