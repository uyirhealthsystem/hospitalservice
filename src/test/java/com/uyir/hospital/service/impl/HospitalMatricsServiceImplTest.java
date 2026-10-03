package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.HospitalMatricsResponse;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
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
}
