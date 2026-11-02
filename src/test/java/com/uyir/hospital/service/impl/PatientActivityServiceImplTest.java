package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.uyir.hospital.dto.PatientActivityItem;
import com.uyir.hospital.dto.PatientActivityResponse;
import com.uyir.hospital.mapper.DoctorAppointmentBookingMapper;
import com.uyir.hospital.mapper.EmergencyBookingMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.model.enums.PatientActivityType;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PatientActivityServiceImplTest {

    @Mock
    private EmergencyBookingRepository emergencyBookingRepository;

    @Mock
    private DoctorAppointmentBookingRepository doctorAppointmentBookingRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private PatientActivityServiceImpl service;

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        service = new PatientActivityServiceImpl(emergencyBookingRepository, doctorAppointmentBookingRepository,
                hospitalRepository, doctorRepository, new EmergencyBookingMapper(), new DoctorAppointmentBookingMapper());
    }

    @Test
    void getActivity_mergesBothKindsNewestFirst_withNamesAndSummary() {
        stubBookings(
                List.of(emergency("e1", EmergencyBookingStatus.COMPLETED, now.minus(Duration.ofDays(3))),
                        emergency("e2", EmergencyBookingStatus.ACCEPTED, now.minus(Duration.ofHours(1)))),
                List.of(appointment("a1", AppointmentStatus.CONFIRMED, now.plus(Duration.ofDays(2))),
                        appointment("a2", AppointmentStatus.NO_SHOW, now.minus(Duration.ofDays(10))),
                        appointment("a3", AppointmentStatus.CANCELLED, now.minus(Duration.ofDays(1)))));

        PatientActivityResponse response = service.getActivity("p1", null, PageRequest.of(0, 20));

        assertThat(response.getPatientId()).isEqualTo("p1");
        assertThat(response.getActivities().getContent()).extracting(PatientActivityItem::getId)
                .containsExactly("a1", "e2", "a3", "e1", "a2");
        assertThat(response.getActivities().getTotalElements()).isEqualTo(5);
        assertThat(response.getActivities().isLast()).isTrue();

        PatientActivityItem first = response.getActivities().getContent().get(0);
        assertThat(first.getType()).isEqualTo(PatientActivityType.APPOINTMENT);
        assertThat(first.getHospitalName()).isEqualTo("City Hospital");
        assertThat(first.getDoctorName()).isEqualTo("Dr. Priya");
        assertThat(first.getStatus()).isEqualTo("CONFIRMED");
        assertThat(first.isActive()).isTrue();
        assertThat(first.getAppointment().getId()).isEqualTo("a1");
        assertThat(first.getEmergencyBooking()).isNull();

        PatientActivityItem emergencyItem = response.getActivities().getContent().get(1);
        assertThat(emergencyItem.getType()).isEqualTo(PatientActivityType.EMERGENCY);
        assertThat(emergencyItem.getTitle()).isEqualTo("Chest Pain / Sweating / Palpitation");
        assertThat(emergencyItem.getHospitalName()).isEqualTo("City Hospital");
        assertThat(emergencyItem.getDoctorName()).isNull();
        assertThat(emergencyItem.isActive()).isTrue();
        assertThat(emergencyItem.getEmergencyBooking().getId()).isEqualTo("e2");

        assertThat(response.getSummary().getTotalEmergencies()).isEqualTo(2);
        assertThat(response.getSummary().getActiveEmergencies()).isEqualTo(1);
        assertThat(response.getSummary().getCompletedEmergencies()).isEqualTo(1);
        assertThat(response.getSummary().getTotalAppointments()).isEqualTo(3);
        assertThat(response.getSummary().getUpcomingAppointments()).isEqualTo(1);
        assertThat(response.getSummary().getCancelledAppointments()).isEqualTo(1);
        assertThat(response.getSummary().getMissedAppointments()).isEqualTo(1);
    }

    @Test
    void getActivity_pagesAcrossBothCollectionsWithoutGapsOrRepeats() {
        // Interleaved: a0 e0 a1 e1 a2 e2 (newest first).
        stubBookings(
                List.of(emergency("e0", EmergencyBookingStatus.COMPLETED, now.minus(Duration.ofDays(2))),
                        emergency("e1", EmergencyBookingStatus.COMPLETED, now.minus(Duration.ofDays(4))),
                        emergency("e2", EmergencyBookingStatus.COMPLETED, now.minus(Duration.ofDays(6)))),
                List.of(appointment("a0", AppointmentStatus.COMPLETED, now.minus(Duration.ofDays(1))),
                        appointment("a1", AppointmentStatus.COMPLETED, now.minus(Duration.ofDays(3))),
                        appointment("a2", AppointmentStatus.COMPLETED, now.minus(Duration.ofDays(5)))));

        PatientActivityResponse page0 = service.getActivity("p1", null, PageRequest.of(0, 4));
        PatientActivityResponse page1 = service.getActivity("p1", null, PageRequest.of(1, 4));

        assertThat(page0.getActivities().getContent()).extracting(PatientActivityItem::getId)
                .containsExactly("a0", "e0", "a1", "e1");
        assertThat(page0.getActivities().getTotalElements()).isEqualTo(6);
        assertThat(page0.getActivities().getTotalPages()).isEqualTo(2);
        assertThat(page0.getActivities().isLast()).isFalse();

        assertThat(page1.getActivities().getContent()).extracting(PatientActivityItem::getId)
                .containsExactly("a2", "e2");
        assertThat(page1.getActivities().getPage()).isEqualTo(1);
        assertThat(page1.getActivities().isLast()).isTrue();
    }

    @Test
    void getActivity_pagePastTheEnd_isEmptyAndSkipsFetching() {
        stubBookings(List.of(emergency("e1", EmergencyBookingStatus.REQUESTED, now)), List.of());

        PatientActivityResponse response = service.getActivity("p1", null, PageRequest.of(5, 20));

        assertThat(response.getActivities().getContent()).isEmpty();
        assertThat(response.getActivities().getTotalElements()).isEqualTo(1);
        verify(emergencyBookingRepository, never()).findByPatientId(any(), any());
        verify(doctorAppointmentBookingRepository, never()).findByPatientId(any(), any());
    }

    @Test
    void getActivity_typeFilter_narrowsListAndTotalButNotSummary() {
        stubBookings(
                List.of(emergency("e1", EmergencyBookingStatus.REQUESTED, now)),
                List.of(appointment("a1", AppointmentStatus.CONFIRMED, now.plus(Duration.ofDays(1)))));

        PatientActivityResponse response = service.getActivity("p1", PatientActivityType.EMERGENCY, PageRequest.of(0, 20));

        assertThat(response.getActivities().getContent()).extracting(PatientActivityItem::getId).containsExactly("e1");
        assertThat(response.getActivities().getTotalElements()).isEqualTo(1);
        assertThat(response.getSummary().getTotalAppointments()).isEqualTo(1);
        verify(doctorAppointmentBookingRepository, never()).findByPatientId(any(), any());
    }

    @Test
    void getActivity_confirmedAppointmentWhoseTimeHasPassed_isNotActive() {
        stubBookings(List.of(), List.of(appointment("a1", AppointmentStatus.CONFIRMED, now.minus(Duration.ofHours(2)))));

        PatientActivityResponse response = service.getActivity("p1", null, PageRequest.of(0, 20));

        assertThat(response.getActivities().getContent().get(0).isActive()).isFalse();
        assertThat(response.getSummary().getUpcomingAppointments()).isZero();
    }

    @Test
    void getActivity_noBookings_returnsEmptyPage() {
        stubBookings(List.of(), List.of());

        PatientActivityResponse response = service.getActivity("p1", null, PageRequest.of(0, 20));

        assertThat(response.getActivities().getContent()).isEmpty();
        assertThat(response.getActivities().getTotalElements()).isZero();
        assertThat(response.getSummary().getTotalEmergencies()).isZero();
    }

    // Repository stand-ins that behave like Mongo: filter by status, sort newest first, honour the page window.
    @SuppressWarnings("unchecked")
    private void stubBookings(List<EmergencyBooking> emergencies, List<DoctorAppointmentBooking> appointments) {
        lenient().when(emergencyBookingRepository.countByPatientId("p1")).thenReturn((long) emergencies.size());
        lenient().when(emergencyBookingRepository.countByPatientIdAndStatusIn(eq("p1"), anyCollection()))
                .thenAnswer(inv -> emergencies.stream()
                        .filter(e -> ((Collection<EmergencyBookingStatus>) inv.getArgument(1)).contains(e.getStatus()))
                        .count());
        lenient().when(emergencyBookingRepository.findByPatientId(eq("p1"), any(Pageable.class)))
                .thenAnswer(inv -> emergencies.stream()
                        .sorted(Comparator.comparing(EmergencyBooking::getRequestedAt).reversed())
                        .limit(((Pageable) inv.getArgument(1)).getPageSize())
                        .toList());

        lenient().when(doctorAppointmentBookingRepository.countByPatientId("p1")).thenReturn((long) appointments.size());
        lenient().when(doctorAppointmentBookingRepository.countByPatientIdAndStatusIn(eq("p1"), anyCollection()))
                .thenAnswer(inv -> appointments.stream()
                        .filter(a -> ((Collection<AppointmentStatus>) inv.getArgument(1)).contains(a.getStatus()))
                        .count());
        lenient().when(doctorAppointmentBookingRepository
                        .countByPatientIdAndStatusInAndAppointmentDateTimeAfter(eq("p1"), anyCollection(), any()))
                .thenAnswer(inv -> appointments.stream()
                        .filter(a -> ((Collection<AppointmentStatus>) inv.getArgument(1)).contains(a.getStatus()))
                        .filter(a -> a.getAppointmentDateTime().isAfter(inv.getArgument(2)))
                        .count());
        lenient().when(doctorAppointmentBookingRepository.findByPatientId(eq("p1"), any(Pageable.class)))
                .thenAnswer(inv -> appointments.stream()
                        .sorted(Comparator.comparing(DoctorAppointmentBooking::getAppointmentDateTime).reversed())
                        .limit(((Pageable) inv.getArgument(1)).getPageSize())
                        .toList());

        Hospital hospital = new Hospital();
        hospital.setId("h1");
        hospital.setHospitalName("City Hospital");
        lenient().when(hospitalRepository.findAllById(any())).thenReturn(List.of(hospital));
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        doctor.setName("Dr. Priya");
        lenient().when(doctorRepository.findAllById(any())).thenReturn(List.of(doctor));
    }

    private EmergencyBooking emergency(String id, EmergencyBookingStatus status, Instant requestedAt) {
        return EmergencyBooking.builder()
                .id(id)
                .patientId("p1")
                .patientName("John Doe")
                .hospitalId("h1")
                .emergencyType("Chest Pain / Sweating / Palpitation")
                .status(status)
                .requestedAt(requestedAt)
                .build();
    }

    private DoctorAppointmentBooking appointment(String id, AppointmentStatus status, Instant at) {
        return DoctorAppointmentBooking.builder()
                .id(id)
                .patientId("p1")
                .patientName("John Doe")
                .hospitalId("h1")
                .doctorId("d1")
                .reason("Fever")
                .status(status)
                .appointmentDateTime(at)
                .build();
    }
}
