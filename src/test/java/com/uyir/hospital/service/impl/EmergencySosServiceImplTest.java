package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.EmergencyHospitalSuggestion;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;

@ExtendWith(MockitoExtension.class)
class EmergencySosServiceImplTest {

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private EmergencySosServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new EmergencySosServiceImpl(hospitalRepository, doctorRepository);
    }

    private Hospital hospitalHandling(String id, boolean active, String... conditions) {
        return Hospital.builder()
                .id(id)
                .hospitalName("Hospital " + id)
                .active(active)
                .emergencyServices(EmergencyServices.builder()
                        .handlesEmergencies(true)
                        .specialtyEmergencyConditionsHandled(List.of(conditions))
                        .build())
                .build();
    }

    private Doctor activeDoctorAt(String hospitalId, String... specialties) {
        return Doctor.builder()
                .id("doc-" + hospitalId)
                .active(true)
                .currentHospitalId(hospitalId)
                .specialties(List.of(specialties))
                .build();
    }

    @Test
    void findAvailableHospitals_matchesByEmergencyTypeCaseInsensitively() {
        Hospital hospital = hospitalHandling("h1", true, "Cardiac Arrest");
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(hospital));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1"))
                .thenReturn(List.of(activeDoctorAt("h1", "Cardiology")));

        List<EmergencyHospitalSuggestion> result =
                service.findAvailableHospitals("cardiac arrest", 80.2, 13.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHospitalId()).isEqualTo("h1");
        assertThat(result.get(0).getAvailableDoctorCount()).isEqualTo(1);
        assertThat(result.get(0).getAvailableSpecialties()).containsExactly("Cardiology");
    }

    @Test
    void findAvailableHospitals_excludesInactiveHospitals() {
        Hospital hospital = hospitalHandling("h1", false, "Cardiac Arrest");
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(hospital));

        List<EmergencyHospitalSuggestion> result =
                service.findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void findAvailableHospitals_excludesHospitalsNotHandlingEmergencies() {
        Hospital noEmergencyServices = Hospital.builder().id("h1").active(true).build();
        Hospital emergenciesDisabled = Hospital.builder()
                .id("h2")
                .active(true)
                .emergencyServices(EmergencyServices.builder().handlesEmergencies(false).build())
                .build();
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(noEmergencyServices, emergenciesDisabled));

        List<EmergencyHospitalSuggestion> result =
                service.findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void findAvailableHospitals_excludesHospitalsNotHandlingRequestedType() {
        Hospital hospital = hospitalHandling("h1", true, "Burns");
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(hospital));

        List<EmergencyHospitalSuggestion> result =
                service.findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void findAvailableHospitals_excludesHospitalsWithNoAvailableDoctors() {
        Hospital hospital = hospitalHandling("h1", true, "Cardiac Arrest");
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(hospital));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1")).thenReturn(List.of());

        List<EmergencyHospitalSuggestion> result =
                service.findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void findAvailableHospitals_doesNotFilterDoctorsBySpecialty_knownGap() {
        // Documents current behavior: a hospital qualifies purely on
        // EmergencyServices.specialtyEmergencyConditionsHandled; any doctor checked in and
        // active is counted as "available" even if their specialty is unrelated to the
        // requested emergencyType (e.g. a dermatologist counted for a cardiac emergency).
        // See EmergencySosServiceImpl.java:33-38 - not fixed here, out of scope.
        Hospital hospital = hospitalHandling("h1", true, "Cardiac Arrest");
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(hospital));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1"))
                .thenReturn(List.of(activeDoctorAt("h1", "Dermatology")));

        List<EmergencyHospitalSuggestion> result =
                service.findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvailableSpecialties()).containsExactly("Dermatology");
    }
}
