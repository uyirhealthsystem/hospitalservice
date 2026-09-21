package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorAppointmentBookingMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.Instant;
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

    private final DoctorAppointmentBookingMapper mapper = new DoctorAppointmentBookingMapper();

    private DoctorAppointmentBookingServiceImpl service;

    private final Instant future = Instant.now().plus(2, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        service = new DoctorAppointmentBookingServiceImpl(
                doctorAppointmentBookingRepository, hospitalRepository, doctorRepository, mapper);
    }

    private DoctorAppointmentBookingRequest validRequest() {
        return DoctorAppointmentBookingRequest.builder()
                .hospitalId("h1")
                .doctorId("d1")
                .appointmentDateTime(future)
                .patientName("John Doe")
                .patientPhone("9999999999")
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
