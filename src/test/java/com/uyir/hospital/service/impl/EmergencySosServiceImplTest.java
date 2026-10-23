package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import com.uyir.hospital.dto.EmergencyHospitalSuggestion;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.EmergencyCoverage;
import com.uyir.hospital.service.HospitalSeatService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EmergencySosServiceImplTest {

    private static final String CHEST_PAIN = "Chest Pain / Sweating / Palpitation";
    private static final String STROKE = "Stroke / Paralysis / Sudden Weakness";

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private HospitalSeatService hospitalSeatService;

    // The real 16 conditions.
    private final EmergencyConditionCatalog emergencyConditionCatalog = new EmergencyConditionCatalog(JsonMapper.builder().build());

    private EmergencySosServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmergencySosServiceImpl(hospitalRepository, emergencyConditionCatalog,
                new EmergencyCoverage(doctorRepository, hospitalSeatService));
        // Doctors built by doctorAt(h, ...) are "doc-h"; they hold a seat unless a test says otherwise.
        lenient().when(hospitalSeatService.seatedDoctorIds(anyString()))
                .thenAnswer(inv -> Set.of("doc-" + inv.getArgument(0)));
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

    // Active and checked in at hospitalId.
    private Doctor doctorAt(String hospitalId, List<String> emergencyConditions, String... specialties) {
        return Doctor.builder()
                .id("doc-" + hospitalId)
                .active(true)
                .currentHospitalId(hospitalId)
                .emergencyConditionsHandled(emergencyConditions)
                .specialties(List.of(specialties))
                .build();
    }

    private void nearby(Hospital... hospitals) {
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class))).thenReturn(List.of(hospitals));
    }

    @Test
    void allFourRulesMet_hospitalSuggestedWithMatchingDoctors() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1"))
                .thenReturn(List.of(doctorAt("h1", List.of(CHEST_PAIN), "Cardiology")));

        List<EmergencyHospitalSuggestion> result = service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHospitalId()).isEqualTo("h1");
        assertThat(result.get(0).getAvailableDoctorCount()).isEqualTo(1);
        assertThat(result.get(0).getAvailableSpecialties()).containsExactly("Cardiology");
    }

    @Test
    void byConditionCodeOrAnyCase_matchesItsName() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1"))
                .thenReturn(List.of(doctorAt("h1", List.of(CHEST_PAIN))));

        assertThat(service.findAvailableHospitals("EMG001", 80.2, 13.0, 10)).hasSize(1);
        assertThat(service.findAvailableHospitals("chest pain / sweating / palpitation", 80.2, 13.0, 10)).hasSize(1);
    }

    @Test
    void emergencyTypeNotOneOfThe16_rejectedBeforeSearching() {
        assertThatThrownBy(() -> service.findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emergencyType")
                .hasMessageContaining("/api/v1/hospital/emergency-conditions");

        verify(hospitalRepository, never()).findByAddressLocationNear(any(Point.class), any(Distance.class));
    }

    @Test
    void rule1_inactiveHospital_excluded() {
        nearby(hospitalHandling("h1", false, CHEST_PAIN));

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10)).isEmpty();
        verify(doctorRepository, never()).findByCurrentHospitalIdAndActiveTrue(anyString());
    }

    @Test
    void rule1_hospitalNotHandlingEmergencies_excluded() {
        Hospital noEmergencyServices = Hospital.builder().id("h1").active(true).build();
        Hospital emergenciesDisabled = Hospital.builder()
                .id("h2")
                .active(true)
                .emergencyServices(EmergencyServices.builder()
                        .handlesEmergencies(false)
                        .specialtyEmergencyConditionsHandled(List.of(CHEST_PAIN))
                        .build())
                .build();
        nearby(noEmergencyServices, emergenciesDisabled);

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10)).isEmpty();
    }

    @Test
    void rule2_hospitalDidNotChooseCondition_excludedEvenIfDoctorHandlesIt() {
        nearby(hospitalHandling("h1", true, STROKE));

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10)).isEmpty();
        verify(doctorRepository, never()).findByCurrentHospitalIdAndActiveTrue(anyString());
    }

    @Test
    void rule3_onDutyDoctorDidNotChooseCondition_excluded() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1"))
                .thenReturn(List.of(doctorAt("h1", List.of(STROKE), "Neurology")));

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10)).isEmpty();
    }

    @Test
    void rule3_doctorWithNoEmergencyConditions_excluded() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1")).thenReturn(List.of(doctorAt("h1", null)));

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10)).isEmpty();
    }

    @Test
    void rule4_noDoctorCheckedIn_excluded() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1")).thenReturn(List.of());

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10)).isEmpty();
    }

    @Test
    void rule4_checkedInDoctorWithoutASeat_excluded() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1"))
                .thenReturn(List.of(doctorAt("h1", List.of(CHEST_PAIN))));
        when(hospitalSeatService.seatedDoctorIds("h1")).thenReturn(Set.of());

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10)).isEmpty();
    }

    @Test
    void onlyDoctorsHandlingTheCondition_areCounted() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN, STROKE));
        Doctor cardiologist = doctorAt("h1", List.of(CHEST_PAIN), "Cardiology");
        Doctor neurologist = Doctor.builder().id("doc-h1-neuro").active(true).currentHospitalId("h1")
                .emergencyConditionsHandled(List.of(STROKE)).specialties(List.of("Neurology")).build();
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1")).thenReturn(List.of(cardiologist, neurologist));
        when(hospitalSeatService.seatedDoctorIds("h1")).thenReturn(Set.of("doc-h1", "doc-h1-neuro"));

        List<EmergencyHospitalSuggestion> result = service.findAvailableHospitals(STROKE, 80.2, 13.0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvailableDoctorCount()).isEqualTo(1);
        assertThat(result.get(0).getAvailableSpecialties()).containsExactly("Neurology");
    }

    @Test
    void mixedResults_keepNearestFirstOrderAndDropUncovered() {
        nearby(hospitalHandling("h1", true, CHEST_PAIN), hospitalHandling("h2", true, CHEST_PAIN), hospitalHandling("h3", true, CHEST_PAIN));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h1")).thenReturn(List.of(doctorAt("h1", List.of(CHEST_PAIN))));
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h2")).thenReturn(List.of());
        when(doctorRepository.findByCurrentHospitalIdAndActiveTrue("h3")).thenReturn(List.of(doctorAt("h3", List.of(CHEST_PAIN))));

        assertThat(service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, 10))
                .extracting(EmergencyHospitalSuggestion::getHospitalId)
                .containsExactly("h1", "h3");
    }

    @Test
    void negativeRadius_throwsIllegalArgumentWithoutQueryingRepository() {
        assertThatThrownBy(() -> service.findAvailableHospitals(CHEST_PAIN, 80.2, 13.0, -5.0))
                .isInstanceOf(IllegalArgumentException.class);

        verify(hospitalRepository, never()).findByAddressLocationNear(any(Point.class), any(Distance.class));
    }

    @Test
    void coordinatesOutOfRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.findAvailableHospitals(CHEST_PAIN, 280.0, 13.0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
