package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.uyir.hospital.model.enums.PatientRelationship;
import com.uyir.hospital.model.enums.Sex;
import com.uyir.hospital.model.enums.VisitType;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.security.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoctorAppointmentBookingServiceImplTest {

    @Mock
    private DoctorAppointmentBookingRepository doctorAppointmentBookingRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentTokenGenerator appointmentTokenGenerator;

    private final DoctorAppointmentBookingMapper mapper = new DoctorAppointmentBookingMapper();

    private DoctorAppointmentBookingServiceImpl service;

    private final Instant future = Instant.now().plus(2, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        service = new DoctorAppointmentBookingServiceImpl(
                doctorAppointmentBookingRepository, hospitalRepository, doctorRepository, mapper, appointmentTokenGenerator);
    }

    private DoctorAppointmentBookingRequest validRequest() {
        return DoctorAppointmentBookingRequest.builder()
                .hospitalId("h1")
                .doctorId("d1")
                .appointmentDateTime(future)
                .patientName("John Doe")
                .patientPhone("9999999999")
                .patientAge(34)
                .patientGender(Sex.MALE)
                .build();
    }

    private Doctor associatedActiveDoctor() {
        return Doctor.builder()
                .id("d1")
                .active(true)
                .hospitalAssociations(List.of(
                        HospitalAssociation.builder().hospitalId("h1").build()))
                .build();
    }

    @Test
    void create_validRequest_savesConfirmedBookingForPatient() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAppointmentBookingResponse response = service.create("p1", validRequest());

        assertThat(response.getPatientId()).isEqualTo("p1");
        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void create_omittedOptionalFields_appliesDefaults() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAppointmentBookingResponse response = service.create("p1", validRequest());

        assertThat(response.getBookedFor()).isEqualTo(BookedFor.SELF);
        assertThat(response.getVisitType()).isEqualTo(VisitType.NEW);
        assertThat(response.getConsultationType()).isEqualTo(ConsultationType.IN_PERSON);
        assertThat(response.getDurationMinutes()).isEqualTo(DoctorAppointmentBooking.DEFAULT_DURATION_MINUTES);
        assertThat(response.getPatientAge()).isEqualTo(34);
        assertThat(response.getPatientGender()).isEqualTo(Sex.MALE);
    }

    @Test
    void create_assignsTokenFromGenerator() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        when(appointmentTokenGenerator.nextToken("d1", future)).thenReturn(7);
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create("p1", validRequest()).getTokenNumber()).isEqualTo(7);
    }

    @Test
    void create_slotClash_doesNotConsumeToken() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        when(doctorAppointmentBookingRepository.findByDoctorIdStatusInStartingBetween(
                        eq("d1"), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(DoctorAppointmentBooking.builder()
                        .id("a0")
                        .appointmentDateTime(future)
                        .status(AppointmentStatus.CONFIRMED)
                        .build()));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(DuplicateResourceException.class);
        verify(appointmentTokenGenerator, never()).nextToken(any(), any());
    }

    @Test
    void reschedule_sameDay_keepsToken() {
        Instant original = LocalDate.now(AppointmentTokenGenerator.ZONE)
                .plusDays(2)
                .atTime(10, 0)
                .atZone(AppointmentTokenGenerator.ZONE)
                .toInstant();
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .doctorId("d1")
                .appointmentDateTime(original)
                .tokenNumber(4)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAppointmentBookingResponse response = service.reschedule("a1", "h1",
                RescheduleAppointmentRequest.builder()
                        .appointmentDateTime(original.plus(3, ChronoUnit.HOURS))
                        .build());

        assertThat(response.getTokenNumber()).isEqualTo(4);
        verify(appointmentTokenGenerator, never()).nextToken(any(), any());
    }

    @Test
    void reschedule_toAnotherDay_issuesNewToken() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .doctorId("d1")
                .appointmentDateTime(future)
                .tokenNumber(4)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        Instant nextDay = future.plus(1, ChronoUnit.DAYS);
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(appointmentTokenGenerator.nextToken("d1", nextDay)).thenReturn(12);
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAppointmentBookingResponse response = service.reschedule(
                "a1", "h1", RescheduleAppointmentRequest.builder().appointmentDateTime(nextDay).build());

        assertThat(response.getTokenNumber()).isEqualTo(12);
    }

    @Test
    void create_overlappingDoctorSlot_throwsConflict() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        DoctorAppointmentBooking existing = DoctorAppointmentBooking.builder()
                .id("a0")
                .doctorId("d1")
                .appointmentDateTime(future.minus(10, ChronoUnit.MINUTES))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findByDoctorIdStatusInStartingBetween(
                        eq("d1"), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(DuplicateResourceException.class);
        verify(doctorAppointmentBookingRepository, never()).save(any());
    }

    @Test
    void create_adjacentDoctorSlot_isAllowed() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        DoctorAppointmentBooking endsExactlyAtStart = DoctorAppointmentBooking.builder()
                .id("a0")
                .doctorId("d1")
                .appointmentDateTime(future.minus(15, ChronoUnit.MINUTES))
                .durationMinutes(15)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findByDoctorIdStatusInStartingBetween(
                        eq("d1"), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(endsExactlyAtStart));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create("p1", validRequest()).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void create_familyMemberWithoutRelationship_throwsIllegalArgument() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        DoctorAppointmentBookingRequest request = validRequest();
        request.setBookedFor(BookedFor.FAMILY_MEMBER);

        assertThatThrownBy(() -> service.create("p1", request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_familyMemberWithRelationship_savesRelationship() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DoctorAppointmentBookingRequest request = validRequest();
        request.setBookedFor(BookedFor.FAMILY_MEMBER);
        request.setRelationship(PatientRelationship.PARENT);

        DoctorAppointmentBookingResponse response = service.create("p1", request);

        assertThat(response.getBookedFor()).isEqualTo(BookedFor.FAMILY_MEMBER);
        assertThat(response.getRelationship()).isEqualTo(PatientRelationship.PARENT);
    }

    @Test
    void create_followUpWithoutPreviousAppointment_throwsIllegalArgument() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        DoctorAppointmentBookingRequest request = validRequest();
        request.setVisitType(VisitType.FOLLOW_UP);

        assertThatThrownBy(() -> service.create("p1", request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_followUpOfAnotherPatientsAppointment_throwsForbidden() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        when(doctorAppointmentBookingRepository.findById("prev")).thenReturn(Optional.of(DoctorAppointmentBooking.builder()
                .id("prev")
                .patientId("someoneElse")
                .status(AppointmentStatus.COMPLETED)
                .build()));
        DoctorAppointmentBookingRequest request = validRequest();
        request.setVisitType(VisitType.FOLLOW_UP);
        request.setPreviousAppointmentId("prev");

        assertThatThrownBy(() -> service.create("p1", request)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_followUpOfCompletedAppointment_savesLink() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(associatedActiveDoctor()));
        when(doctorAppointmentBookingRepository.findById("prev")).thenReturn(Optional.of(DoctorAppointmentBooking.builder()
                .id("prev")
                .patientId("p1")
                .status(AppointmentStatus.COMPLETED)
                .build()));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DoctorAppointmentBookingRequest request = validRequest();
        request.setVisitType(VisitType.FOLLOW_UP);
        request.setPreviousAppointmentId("prev");

        DoctorAppointmentBookingResponse response = service.create("p1", request);

        assertThat(response.getVisitType()).isEqualTo(VisitType.FOLLOW_UP);
        assertThat(response.getPreviousAppointmentId()).isEqualTo("prev");
    }

    @Test
    void create_hospitalMissing_throwsNotFound() {
        when(hospitalRepository.existsById("h1")).thenReturn(false);

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_doctorNotAssociatedWithHospital_throwsIllegalArgument() {
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        Doctor unassociatedDoctor = Doctor.builder()
                .id("d1")
                .active(true)
                .hospitalAssociations(List.of(
                        HospitalAssociation.builder().hospitalId("other").build()))
                .build();
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(unassociatedDoctor));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByPatientId_returnsMappedBookings() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .patientId("p1")
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findByPatientIdOrderByAppointmentDateTimeDesc("p1"))
                .thenReturn(List.of(booking));

        List<DoctorAppointmentBookingResponse> result = service.getByPatientId("p1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("a1");
    }

    @Test
    void reschedule_ownedActiveBooking_updatesDateTimeAndStatus() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant newTime = future.plus(1, ChronoUnit.DAYS);
        DoctorAppointmentBookingResponse response = service.reschedule(
                "a1", "h1", RescheduleAppointmentRequest.builder().appointmentDateTime(newTime).build());

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(response.getAppointmentDateTime()).isEqualTo(newTime);
    }

    @Test
    void reschedule_bookingBelongsToOtherHospital_throwsForbidden() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.reschedule(
                        "a1", "h2", RescheduleAppointmentRequest.builder().appointmentDateTime(future).build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void reschedule_newTimeClashesWithAnotherBooking_throwsConflict() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .doctorId("d1")
                .status(AppointmentStatus.CONFIRMED)
                .build();
        DoctorAppointmentBooking other = DoctorAppointmentBooking.builder()
                .id("a2")
                .doctorId("d1")
                .appointmentDateTime(future)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(doctorAppointmentBookingRepository.findByDoctorIdStatusInStartingBetween(
                        eq("d1"), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(other));

        assertThatThrownBy(() -> service.reschedule(
                        "a1", "h1", RescheduleAppointmentRequest.builder().appointmentDateTime(future).build()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void reschedule_overlapsOnlyItself_isAllowed() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .doctorId("d1")
                .appointmentDateTime(future)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(doctorAppointmentBookingRepository.findByDoctorIdStatusInStartingBetween(
                        eq("d1"), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(booking));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant shifted = future.plus(5, ChronoUnit.MINUTES);
        DoctorAppointmentBookingResponse response = service.reschedule(
                "a1", "h1", RescheduleAppointmentRequest.builder().appointmentDateTime(shifted).build());

        assertThat(response.getAppointmentDateTime()).isEqualTo(shifted);
    }

    @Test
    void cancel_byOwningPatient_recordsReasonAndCanceller() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .patientId("p1")
                .hospitalId("h1")
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAppointmentBookingResponse response = service.cancel(
                "a1", "p1", Role.PATIENT, CancelAppointmentRequest.builder().reason("Feeling better").build());

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.getCancellationReason()).isEqualTo("Feeling better");
        assertThat(response.getCancelledBy()).isEqualTo(CancelledBy.PATIENT);
        assertThat(response.getCancelledAt()).isNotNull();
    }

    @Test
    void cancel_byOwningHospital_recordsHospitalAsCanceller() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .patientId("p1")
                .hospitalId("h1")
                .status(AppointmentStatus.RESCHEDULED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAppointmentBookingResponse response = service.cancel(
                "a1", "h1", Role.HOSPITAL, CancelAppointmentRequest.builder().reason("Doctor on leave").build());

        assertThat(response.getCancelledBy()).isEqualTo(CancelledBy.HOSPITAL);
    }

    @Test
    void cancel_byOtherPatient_throwsForbidden() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .patientId("p1")
                .hospitalId("h1")
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(
                        "a1", "p2", Role.PATIENT, CancelAppointmentRequest.builder().reason("x").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_completedBooking_throwsIllegalArgument() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .patientId("p1")
                .hospitalId("h1")
                .status(AppointmentStatus.COMPLETED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(
                        "a1", "p1", Role.PATIENT, CancelAppointmentRequest.builder().reason("x").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_noShowAfterScheduledTime_updatesStatus() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .appointmentDateTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));
        when(doctorAppointmentBookingRepository.save(any(DoctorAppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAppointmentBookingResponse response = service.updateStatus(
                "a1", "h1", UpdateAppointmentStatusRequest.builder().status(AppointmentStatus.NO_SHOW).build());

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void updateStatus_beforeScheduledTime_throwsIllegalArgument() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .appointmentDateTime(future)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.updateStatus(
                        "a1", "h1", UpdateAppointmentStatusRequest.builder().status(AppointmentStatus.NO_SHOW).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_toCancelled_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.updateStatus(
                        "a1", "h1", UpdateAppointmentStatusRequest.builder().status(AppointmentStatus.CANCELLED).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reschedule_cancelledBooking_throwsIllegalArgument() {
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .id("a1")
                .hospitalId("h1")
                .status(AppointmentStatus.CANCELLED)
                .build();
        when(doctorAppointmentBookingRepository.findById("a1")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.reschedule(
                        "a1", "h1", RescheduleAppointmentRequest.builder().appointmentDateTime(future).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
