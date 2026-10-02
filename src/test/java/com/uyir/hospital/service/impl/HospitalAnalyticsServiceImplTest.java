package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.AnalyticsSnapshotRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotResponse;
import com.uyir.hospital.dto.AnalyticsTrendPoint;
import com.uyir.hospital.dto.DistrictAnalyticsSummary;
import com.uyir.hospital.dto.HospitalAnalyticsResponse;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.AnalyticsSnapshot;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.BedCapacity;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.embedded.Facilities;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.repository.AnalyticsSnapshotRepository;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HospitalAnalyticsServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 3);

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private EmergencyBookingRepository emergencyBookingRepository;

    @Mock
    private DoctorAppointmentBookingRepository appointmentBookingRepository;

    @Mock
    private AnalyticsSnapshotRepository snapshotRepository;

    private HospitalAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HospitalAnalyticsServiceImpl(
                hospitalRepository, doctorRepository, emergencyBookingRepository, appointmentBookingRepository, snapshotRepository);
    }

    private static Hospital hospital(String id, boolean active, int general, int icu, boolean emergencies) {
        return Hospital.builder()
                .id(id)
                .hospitalName("Hospital " + id)
                .hospitalType(HospitalType.HOSPITAL)
                .active(active)
                .address(Address.builder().city("Chennai").district("Chennai").build())
                .facilities(Facilities.builder()
                        .bedCapacity(BedCapacity.builder().general(general).icu(icu).build())
                        .build())
                .emergencyServices(EmergencyServices.builder()
                        .handlesEmergencies(emergencies)
                        .ambulanceAvailable(emergencies)
                        .build())
                .build();
    }

    private static Doctor doctor(String id, String currentHospitalId, String... associatedHospitalIds) {
        return Doctor.builder()
                .id(id)
                .active(true)
                .currentHospitalId(currentHospitalId)
                .hospitalAssociations(Arrays.stream(associatedHospitalIds)
                        .map(h -> HospitalAssociation.builder().hospitalId(h).active(true).build())
                        .toList())
                .build();
    }

    private static Instant ist(int day, int hour) {
        return LocalDateTime.of(2026, 9, day, hour, 0).atZone(HospitalAnalyticsServiceImpl.ZONE).toInstant();
    }

    private static EmergencyBooking emergency(String hospitalId, String type, EmergencyBookingStatus status, Instant at) {
        return EmergencyBooking.builder().hospitalId(hospitalId).emergencyType(type).status(status).requestedAt(at).build();
    }

    private static DoctorAppointmentBooking appointment(String hospitalId, AppointmentStatus status, Instant at) {
        return DoctorAppointmentBooking.builder().hospitalId(hospitalId).status(status).appointmentDateTime(at).build();
    }

    private void stubDistrict() {
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Chennai"))
                .thenReturn(List.of(hospital("h1", true, 50, 10, true), hospital("h2", false, 20, 5, false)));
        when(doctorRepository.findByHospitalAssociationsHospitalIdInAndActiveTrue(anyCollection()))
                .thenReturn(List.of(doctor("d1", "h1", "h1", "h2"), doctor("d2", null, "h2")));
        when(emergencyBookingRepository.findByHospitalIdsRequestedBetween(anyCollection(), any(), any()))
                .thenReturn(List.of(
                        emergency("h1", "Cardiac Arrest", EmergencyBookingStatus.REQUESTED, ist(1, 10)),
                        emergency("h1", "Cardiac Arrest", EmergencyBookingStatus.COMPLETED, ist(1, 23)),
                        emergency("h1", "Stroke", EmergencyBookingStatus.REQUESTED, ist(3, 2))));
        when(appointmentBookingRepository.findByHospitalIdsScheduledBetween(anyCollection(), any(), any()))
                .thenReturn(List.of(appointment("h2", AppointmentStatus.CONFIRMED, ist(2, 9))));
    }

    @Test
    void getSummary_aggregatesDistrict() {
        stubDistrict();

        DistrictAnalyticsSummary summary = service.getSummary("Chennai", FROM, TO);

        assertThat(summary.getHospitals().getTotal()).isEqualTo(2);
        assertThat(summary.getHospitals().getActive()).isEqualTo(1);
        assertThat(summary.getHospitals().getInactive()).isEqualTo(1);
        assertThat(summary.getHospitals().getHandlingEmergencies()).isEqualTo(1);
        assertThat(summary.getHospitals().getByType())
                .containsEntry(HospitalType.HOSPITAL, 2L)
                .containsEntry(HospitalType.CLINIC, 0L);
        // Inactive h2's beds are excluded from capacity
        assertThat(summary.getBeds().getGeneral()).isEqualTo(50);
        assertThat(summary.getBeds().getIcu()).isEqualTo(10);
        assertThat(summary.getBeds().getTotal()).isEqualTo(60);
        assertThat(summary.getDoctors().getAssociated()).isEqualTo(2);
        assertThat(summary.getDoctors().getCheckedInNow()).isEqualTo(1);
        assertThat(summary.getEmergencyBookings().getTotal()).isEqualTo(3);
        assertThat(summary.getEmergencyBookings().getByStatus())
                .containsEntry(EmergencyBookingStatus.REQUESTED, 2L)
                .containsEntry(EmergencyBookingStatus.CANCELLED, 0L);
        assertThat(summary.getEmergencyBookings().getByEmergencyType().keySet())
                .containsExactly("Cardiac Arrest", "Stroke");
        assertThat(summary.getAppointments().getTotal()).isEqualTo(1);
    }

    @Test
    void getSummary_queriesIstDayBoundaries() {
        stubDistrict();

        service.getSummary("Chennai", FROM, TO);

        verify(emergencyBookingRepository).findByHospitalIdsRequestedBetween(
                anyCollection(), eq(Instant.parse("2026-08-31T18:30:00Z")), eq(Instant.parse("2026-09-03T18:30:00Z")));
    }

    @Test
    void getSummary_noHospitalsInDistrict_returnsZerosWithoutQueryingBookings() {
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Nowhere")).thenReturn(List.of());

        DistrictAnalyticsSummary summary = service.getSummary("Nowhere", FROM, TO);

        assertThat(summary.getHospitals().getTotal()).isZero();
        assertThat(summary.getEmergencyBookings().getTotal()).isZero();
        verifyNoInteractions(emergencyBookingRepository, appointmentBookingRepository, doctorRepository);
    }

    @Test
    void getSummary_fromAfterTo_throws() {
        assertThatThrownBy(() -> service.getSummary("Chennai", TO, FROM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getSummary_rangeTooLong_throws() {
        assertThatThrownBy(() -> service.getSummary("Chennai", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    @Test
    void getSummary_noDates_defaultsToLast30Days() {
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Chennai")).thenReturn(List.of());

        DistrictAnalyticsSummary summary = service.getSummary("Chennai", null, null);

        assertThat(summary.getToDate()).isEqualTo(LocalDate.now(HospitalAnalyticsServiceImpl.ZONE));
        assertThat(summary.getFromDate()).isEqualTo(summary.getToDate().minusDays(29));
    }

    @Test
    void getHospitalBreakdown_perHospitalCounts() {
        stubDistrict();

        List<HospitalAnalyticsResponse> rows = service.getHospitalBreakdown("Chennai", FROM, TO);

        assertThat(rows).extracting(HospitalAnalyticsResponse::getHospitalId).containsExactly("h1", "h2");
        HospitalAnalyticsResponse h1 = rows.get(0);
        assertThat(h1.getEmergencyBookings()).isEqualTo(3);
        assertThat(h1.getAssociatedDoctors()).isEqualTo(1);
        assertThat(h1.getCheckedInDoctors()).isEqualTo(1);
        HospitalAnalyticsResponse h2 = rows.get(1);
        assertThat(h2.getAppointments()).isEqualTo(1);
        assertThat(h2.getAssociatedDoctors()).isEqualTo(2);
        assertThat(h2.getCheckedInDoctors()).isZero();
    }

    @Test
    void getHospitalAnalytics_otherDistrict_throwsForbidden() {
        Hospital other = hospital("h9", true, 1, 1, false);
        other.getAddress().setDistrict("Madurai");
        when(hospitalRepository.findById("h9")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.getHospitalAnalytics("Chennai", "h9", FROM, TO))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getHospitalAnalytics_unknownHospital_throwsNotFound() {
        when(hospitalRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHospitalAnalytics("Chennai", "missing", FROM, TO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTrends_bucketsByIstDayIncludingZeroDays() {
        stubDistrict();

        List<AnalyticsTrendPoint> points = service.getTrends("Chennai", FROM, TO);

        assertThat(points).extracting(AnalyticsTrendPoint::getDate).containsExactly(FROM, FROM.plusDays(1), TO);
        // 23:00 IST on the 1st is 17:30 UTC - still the 1st locally
        assertThat(points.get(0).getEmergencyBookings()).isEqualTo(2);
        assertThat(points.get(1).getEmergencyBookings()).isZero();
        assertThat(points.get(1).getAppointments()).isEqualTo(1);
        assertThat(points.get(2).getEmergencyBookings()).isEqualTo(1);
    }

    @Test
    void createSnapshot_savesSummaryForDistrictAndAdmin() {
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Chennai")).thenReturn(List.of());
        when(snapshotRepository.save(any(AnalyticsSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnalyticsSnapshotResponse response = service.createSnapshot(
                "Chennai", "admin1", AnalyticsSnapshotRequest.builder().label("Sept").fromDate(FROM).toDate(TO).build());

        assertThat(response.getDistrict()).isEqualTo("Chennai");
        assertThat(response.getCreatedBy()).isEqualTo("admin1");
        assertThat(response.getLabel()).isEqualTo("Sept");
        assertThat(response.getFromDate()).isEqualTo(FROM);
        assertThat(response.getSummary()).isNotNull();
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void getSnapshot_otherDistrict_throwsForbidden() {
        when(snapshotRepository.findById("s1"))
                .thenReturn(Optional.of(AnalyticsSnapshot.builder().id("s1").district("Madurai").build()));

        assertThatThrownBy(() -> service.getSnapshot("Chennai", "s1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteSnapshot_ownDistrict_deletes() {
        AnalyticsSnapshot snapshot = AnalyticsSnapshot.builder().id("s1").district("chennai").build();
        when(snapshotRepository.findById("s1")).thenReturn(Optional.of(snapshot));

        service.deleteSnapshot("Chennai", "s1");

        verify(snapshotRepository).delete(snapshot);
    }

    @Test
    void deleteSnapshot_otherDistrict_doesNotDelete() {
        when(snapshotRepository.findById("s1"))
                .thenReturn(Optional.of(AnalyticsSnapshot.builder().id("s1").district("Madurai").build()));

        assertThatThrownBy(() -> service.deleteSnapshot("Chennai", "s1")).isInstanceOf(ForbiddenException.class);
        verify(snapshotRepository, never()).delete(any());
    }
}
