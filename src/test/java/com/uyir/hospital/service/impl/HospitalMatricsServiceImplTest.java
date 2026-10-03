package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.HospitalMatricsResponse;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HospitalMatricsServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 3);
    // IST day boundaries: 2026-09-01T00:00+05:30 .. 2026-09-04T00:00+05:30
    private static final Instant START = Instant.parse("2026-08-31T18:30:00Z");
    private static final Instant END = Instant.parse("2026-09-03T18:30:00Z");

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private EmergencyBookingRepository emergencyBookingRepository;

    @Mock
    private DoctorAppointmentBookingRepository appointmentBookingRepository;

    @InjectMocks
    private HospitalMatricsServiceImpl service;

    @Test
    void getMatrics_aggregatesCounts() {
        when(hospitalRepository.count()).thenReturn(10L);
        when(hospitalRepository.countByActiveTrue()).thenReturn(8L);
        when(hospitalRepository.countByActiveTrueAndEmergencyServicesHandlesEmergenciesTrue()).thenReturn(5L);
        when(doctorRepository.count()).thenReturn(40L);
        when(doctorRepository.countByActiveTrue()).thenReturn(35L);
        when(doctorRepository.countByActiveTrueAndCurrentHospitalIdIsNotNull()).thenReturn(12L);
        when(emergencyBookingRepository.countByStatusRequestedBetween(EmergencyBookingStatus.REQUESTED, START, END))
                .thenReturn(3L);
        when(emergencyBookingRepository.countByStatusRequestedBetween(EmergencyBookingStatus.COMPLETED, START, END))
                .thenReturn(7L);
        when(emergencyBookingRepository.countByStatusRequestedBetween(EmergencyBookingStatus.CANCELLED, START, END))
                .thenReturn(0L);
        when(emergencyBookingRepository.countByStatus(EmergencyBookingStatus.REQUESTED)).thenReturn(4L);
        when(appointmentBookingRepository.countByStatusScheduledBetween(AppointmentStatus.CONFIRMED, START, END))
                .thenReturn(6L);
        when(appointmentBookingRepository.countByStatusScheduledBetween(AppointmentStatus.CANCELLED, START, END))
                .thenReturn(2L);
        when(appointmentBookingRepository.countByStatusScheduledBetween(AppointmentStatus.RESCHEDULED, START, END))
                .thenReturn(0L);
        when(appointmentBookingRepository.countByStatusScheduledBetween(AppointmentStatus.COMPLETED, START, END))
                .thenReturn(0L);
        when(appointmentBookingRepository.countByStatusInScheduledFrom(
                eq(EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED)), any(Instant.class)))
                .thenReturn(9L);

        HospitalMatricsResponse matrics = service.getMatrics(FROM, TO);

        assertThat(matrics.getFromDate()).isEqualTo(FROM);
        assertThat(matrics.getToDate()).isEqualTo(TO);
        assertThat(matrics.getGeneratedAt()).isNotNull();

        assertThat(matrics.getHospitals().getTotal()).isEqualTo(10);
        assertThat(matrics.getHospitals().getActive()).isEqualTo(8);
        assertThat(matrics.getHospitals().getInactive()).isEqualTo(2);
        assertThat(matrics.getHospitals().getHandlingEmergencies()).isEqualTo(5);

        assertThat(matrics.getDoctors().getTotal()).isEqualTo(40);
        assertThat(matrics.getDoctors().getActive()).isEqualTo(35);
        assertThat(matrics.getDoctors().getCheckedInNow()).isEqualTo(12);

        assertThat(matrics.getEmergencyBookings().getTotal()).isEqualTo(10);
        assertThat(matrics.getEmergencyBookings().getByStatus())
                .containsEntry(EmergencyBookingStatus.REQUESTED, 3L)
                .containsEntry(EmergencyBookingStatus.CANCELLED, 0L)
                .containsEntry(EmergencyBookingStatus.COMPLETED, 7L);
        assertThat(matrics.getEmergencyBookings().getOpenNow()).isEqualTo(4);

        assertThat(matrics.getAppointments().getTotal()).isEqualTo(8);
        assertThat(matrics.getAppointments().getByStatus())
                .hasSize(AppointmentStatus.values().length)
                .containsEntry(AppointmentStatus.CONFIRMED, 6L)
                .containsEntry(AppointmentStatus.RESCHEDULED, 0L);
        assertThat(matrics.getAppointments().getUpcoming()).isEqualTo(9);
    }

    @Test
    void getMatrics_withoutDates_defaultsToLast30Days() {
        HospitalMatricsResponse matrics = service.getMatrics(null, null);

        LocalDate today = LocalDate.now(HospitalAnalyticsServiceImpl.ZONE);
        assertThat(matrics.getToDate()).isEqualTo(today);
        assertThat(matrics.getFromDate()).isEqualTo(today.minusDays(29));
        verify(emergencyBookingRepository).countByStatusRequestedBetween(
                EmergencyBookingStatus.REQUESTED,
                today.minusDays(29).atStartOfDay(HospitalAnalyticsServiceImpl.ZONE).toInstant(),
                today.plusDays(1).atStartOfDay(HospitalAnalyticsServiceImpl.ZONE).toInstant());
    }

    @Test
    void getMatrics_fromAfterTo_throwsBeforeQuerying() {
        assertThatThrownBy(() -> service.getMatrics(TO, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromDate must be on or before toDate");
        verifyNoInteractions(hospitalRepository, doctorRepository, emergencyBookingRepository, appointmentBookingRepository);
    }

    @Test
    void getDistrictMatrics_scopesCountsToDistrictHospitals() {
        Hospital h1 = Hospital.builder().id("h1").active(true)
                .emergencyServices(EmergencyServices.builder().handlesEmergencies(true).build()).build();
        Hospital h2 = Hospital.builder().id("h2").active(true).build();
        Hospital h3 = Hospital.builder().id("h3").active(false)
                .emergencyServices(EmergencyServices.builder().handlesEmergencies(true).build()).build();
        Set<String> ids = Set.of("h1", "h2", "h3");
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Chennai")).thenReturn(List.of(h1, h2, h3));
        when(doctorRepository.countByHospitalAssociationsHospitalIdIn(ids)).thenReturn(6L);
        when(doctorRepository.countByHospitalAssociationsHospitalIdInAndActiveTrue(ids)).thenReturn(5L);
        when(doctorRepository.countByCurrentHospitalIdInAndActiveTrue(ids)).thenReturn(2L);
        for (EmergencyBookingStatus status : EmergencyBookingStatus.values()) {
            when(emergencyBookingRepository.countByHospitalIdsStatusRequestedBetween(ids, status, START, END))
                    .thenReturn(status == EmergencyBookingStatus.COMPLETED ? 4L : 1L);
        }
        when(emergencyBookingRepository.countByHospitalIdInAndStatus(ids, EmergencyBookingStatus.REQUESTED))
                .thenReturn(3L);
        for (AppointmentStatus status : AppointmentStatus.values()) {
            when(appointmentBookingRepository.countByHospitalIdsStatusScheduledBetween(ids, status, START, END))
                    .thenReturn(status == AppointmentStatus.CONFIRMED ? 7L : 0L);
        }
        when(appointmentBookingRepository.countByHospitalIdsStatusInScheduledFrom(
                eq(ids), eq(EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED)), any(Instant.class)))
                .thenReturn(8L);

        HospitalMatricsResponse matrics = service.getDistrictMatrics("Chennai", FROM, TO);

        assertThat(matrics.getDistrict()).isEqualTo("Chennai");
        assertThat(matrics.getHospitals().getTotal()).isEqualTo(3);
        assertThat(matrics.getHospitals().getActive()).isEqualTo(2);
        assertThat(matrics.getHospitals().getInactive()).isEqualTo(1);
        // h3 handles emergencies but is inactive
        assertThat(matrics.getHospitals().getHandlingEmergencies()).isEqualTo(1);
        assertThat(matrics.getDoctors().getTotal()).isEqualTo(6);
        assertThat(matrics.getDoctors().getActive()).isEqualTo(5);
        assertThat(matrics.getDoctors().getCheckedInNow()).isEqualTo(2);
        assertThat(matrics.getEmergencyBookings().getTotal()).isEqualTo(6);
        assertThat(matrics.getEmergencyBookings().getByStatus()).containsEntry(EmergencyBookingStatus.COMPLETED, 4L);
        assertThat(matrics.getEmergencyBookings().getOpenNow()).isEqualTo(3);
        assertThat(matrics.getAppointments().getTotal()).isEqualTo(7);
        assertThat(matrics.getAppointments().getUpcoming()).isEqualTo(8);
    }

    @Test
    void getDistrictMatrics_unknownDistrict_returnsZerosWithoutCountQueries() {
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Nowhere")).thenReturn(List.of());

        HospitalMatricsResponse matrics = service.getDistrictMatrics("Nowhere", FROM, TO);

        assertThat(matrics.getDistrict()).isEqualTo("Nowhere");
        assertThat(matrics.getHospitals().getTotal()).isZero();
        assertThat(matrics.getDoctors().getTotal()).isZero();
        assertThat(matrics.getEmergencyBookings().getByStatus()).hasSize(EmergencyBookingStatus.values().length)
                .allSatisfy((status, count) -> assertThat(count).isZero());
        assertThat(matrics.getAppointments().getUpcoming()).isZero();
        verifyNoInteractions(doctorRepository, emergencyBookingRepository, appointmentBookingRepository);
    }

    @Test
    void getDistrictMatrics_fromAfterTo_throwsBeforeQuerying() {
        assertThatThrownBy(() -> service.getDistrictMatrics("Chennai", TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hospitalRepository);
    }
}
