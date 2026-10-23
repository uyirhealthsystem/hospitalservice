package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import com.uyir.hospital.catalog.SpecialtyCatalog;
import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.HospitalMapper;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.embedded.Facilities;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.HospitalSeatService;
import com.uyir.hospital.repository.HospitalSearchCriteria;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class HospitalServiceImplTest {

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private HospitalSeatService hospitalSeatService;

    private final HospitalMapper hospitalMapper = new HospitalMapper();

    private final SpecialtyCatalog specialtyCatalog = new SpecialtyCatalog(JsonMapper.builder().build());

    private final EmergencyConditionCatalog emergencyConditionCatalog = new EmergencyConditionCatalog(JsonMapper.builder().build());

    private HospitalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HospitalServiceImpl(hospitalRepository, hospitalMapper, hospitalSeatService, specialtyCatalog, emergencyConditionCatalog);
    }

    private HospitalRequest.HospitalRequestBuilder validRequestBuilder() {
        return HospitalRequest.builder()
                .hospitalName("City Care Hospital")
                .registrationNumber("REG-123")
                .ownershipType(OwnershipType.PRIVATE)
                .hospitalType(HospitalType.HOSPITAL)
                .address(com.uyir.hospital.model.embedded.Address.builder()
                        .addressLine("1 Main St")
                        .city("Chennai")
                        .state("TN")
                        .country("India")
                        .pincode("600001")
                        .build())
                .contactDetails(com.uyir.hospital.model.embedded.ContactDetails.builder()
                        .phone("9999999999")
                        .build());
    }

    private Hospital existingHospital() {
        return Hospital.builder()
                .id("h1")
                .hospitalName("City Care Hospital")
                .registrationNumber("REG-123")
                .active(true)
                .build();
    }

    @Test
    void create_newRegistrationNumber_savesAsActive() {
        when(hospitalRepository.existsByRegistrationNumber("REG-123")).thenReturn(false);
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> {
            Hospital saved = inv.getArgument(0);
            saved.setId("h1");
            return saved;
        });

        HospitalResponse response = service.create("u1", validRequestBuilder().build());

        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUserId()).isEqualTo("u1");
        verify(hospitalSeatService).ensureFreeSeat("h1");
    }

    @Test
    void create_departmentsByCodeOrName_savedAsOfficialNamesWithoutDuplicates() {
        when(hospitalRepository.existsByRegistrationNumber("REG-123")).thenReturn(false);
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        HospitalResponse response = service.create("u1", validRequestBuilder()
                .facilities(Facilities.builder()
                        .specialtyDepartments(List.of("CAR001", "neurology", "Cardiology"))
                        .build())
                .build());

        assertThat(response.getFacilities().getSpecialtyDepartments()).containsExactly("Cardiology", "Neurology");
    }

    @Test
    void create_departmentNotInMasterList_rejectedBeforeAnyLookupAndNeverSaved() {
        assertThatThrownBy(() -> service.create("u1", validRequestBuilder()
                        .facilities(Facilities.builder().specialtyDepartments(List.of("Cardiology", "Heart Ward")).build())
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("facilities.specialtyDepartments")
                .hasMessageContaining("Heart Ward");

        verify(hospitalRepository, never()).existsByRegistrationNumber(any());
        verify(hospitalRepository, never()).save(any());
    }

    @Test
    void create_emergencyConditionsByCodeOrName_savedAsOfficialNamesWithoutDuplicates() {
        when(hospitalRepository.existsByRegistrationNumber("REG-123")).thenReturn(false);
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        HospitalResponse response = service.create("u1", validRequestBuilder()
                .emergencyServices(EmergencyServices.builder()
                        .handlesEmergencies(true)
                        .specialtyEmergencyConditionsHandled(List.of("EMG001", "stroke / paralysis / sudden weakness", "EMG016", "EMG001"))
                        .build())
                .build());

        assertThat(response.getEmergencyServices().getSpecialtyEmergencyConditionsHandled()).containsExactly(
                "Chest Pain / Sweating / Palpitation", "Stroke / Paralysis / Sudden Weakness", "Other Emergency");
    }

    @Test
    void create_emergencyConditionsAreIndependentOfDepartments() {
        // Departments are for appointments, conditions for emergencies - a Pregnancy emergency needs no
        // Obstetrics department (whether it's covered depends on the doctors on duty, at booking time).
        when(hospitalRepository.existsByRegistrationNumber("REG-123")).thenReturn(false);
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        HospitalResponse response = service.create("u1", validRequestBuilder()
                .facilities(Facilities.builder().specialtyDepartments(List.of("Dermatology")).build())
                .emergencyServices(EmergencyServices.builder()
                        .handlesEmergencies(true)
                        .specialtyEmergencyConditionsHandled(List.of("EMG006"))
                        .build())
                .build());

        assertThat(response.getEmergencyServices().getSpecialtyEmergencyConditionsHandled())
                .containsExactly("Pregnancy Related Emergency");
        assertThat(response.getFacilities().getSpecialtyDepartments()).containsExactly("Dermatology");
    }

    @Test
    void create_unknownEmergencyCondition_rejected() {
        assertThatThrownBy(() -> service.create("u1", validRequestBuilder()
                        .emergencyServices(EmergencyServices.builder()
                                .specialtyEmergencyConditionsHandled(List.of("Cardiac Arrest"))
                                .build())
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emergencyServices.specialtyEmergencyConditionsHandled")
                .hasMessageContaining("Cardiac Arrest")
                .hasMessageContaining("/api/v1/hospital/emergency-conditions");

        verify(hospitalRepository, never()).save(any());
    }

    @Test
    void create_facilitiesWithoutDepartments_isAllowed() {
        when(hospitalRepository.existsByRegistrationNumber("REG-123")).thenReturn(false);
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        HospitalResponse response = service.create("u1", validRequestBuilder()
                .facilities(Facilities.builder().build())
                .build());

        assertThat(response.getFacilities().getSpecialtyDepartments()).isNull();
    }

    @Test
    void update_departmentNotInMasterList_rejectedAndNeverSaved() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(existingHospital()));

        assertThatThrownBy(() -> service.update("h1", validRequestBuilder()
                        .facilities(Facilities.builder().specialtyDepartments(List.of("Heart Ward")).build())
                        .build()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(hospitalRepository, never()).save(any());
    }

    @Test
    void update_departmentCodes_savedAsOfficialNames() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(existingHospital()));
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        HospitalResponse response = service.update("h1", validRequestBuilder()
                .facilities(Facilities.builder().specialtyDepartments(List.of("ORT001", "EMC001")).build())
                .build());

        assertThat(response.getFacilities().getSpecialtyDepartments()).containsExactly("Orthopedics", "Emergency Medicine");
    }

    @Test
    void create_duplicateRegistrationNumber_throwsAndNeverSaves() {
        when(hospitalRepository.existsByRegistrationNumber("REG-123")).thenReturn(true);

        assertThatThrownBy(() -> service.create("u1", validRequestBuilder().build()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(hospitalRepository, never()).save(any());
    }

    @Test
    void create_userAlreadyOwnsAHospital_throwsAndNeverSaves() {
        when(hospitalRepository.existsByUserId("u1")).thenReturn(true);

        assertThatThrownBy(() -> service.create("u1", validRequestBuilder().build()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("u1");

        verify(hospitalRepository, never()).save(any());
    }

    @Test
    void getById_found_mapsToResponse() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(existingHospital()));

        assertThat(service.getById("h1").getId()).isEqualTo("h1");
    }

    @Test
    void getById_notFound_throws() {
        when(hospitalRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void search_buildsCriteriaFromArgsAndDelegates() {
        Pageable pageable = PageRequest.of(0, 20);
        when(hospitalRepository.search(any(HospitalSearchCriteria.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(existingHospital()), pageable, 1));

        var result = service.search("Chennai", "TN", HospitalType.HOSPITAL, OwnershipType.PRIVATE, true, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ArgumentCaptor<HospitalSearchCriteria> captor = ArgumentCaptor.forClass(HospitalSearchCriteria.class);
        verify(hospitalRepository).search(captor.capture(), eq(pageable));
        assertThat(captor.getValue().getCity()).isEqualTo("Chennai");
        assertThat(captor.getValue().getState()).isEqualTo("TN");
        assertThat(captor.getValue().getHospitalType()).isEqualTo(HospitalType.HOSPITAL);
        assertThat(captor.getValue().getOwnershipType()).isEqualTo(OwnershipType.PRIVATE);
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void update_registrationNumberUnchanged_skipsDuplicateCheck() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(existingHospital()));
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("h1", validRequestBuilder().registrationNumber("REG-123").build());

        verify(hospitalRepository, never()).existsByRegistrationNumber(any());
    }

    @Test
    void update_registrationNumberChangedToExisting_throwsDuplicate() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(existingHospital()));
        when(hospitalRepository.existsByRegistrationNumber("REG-999")).thenReturn(true);

        assertThatThrownBy(() -> service.update("h1", validRequestBuilder().registrationNumber("REG-999").build()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(hospitalRepository, never()).save(any());
    }

    @Test
    void update_notFound_throws() {
        when(hospitalRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", validRequestBuilder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivate_setsActiveFalse() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(existingHospital()));
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate("h1");

        ArgumentCaptor<Hospital> captor = ArgumentCaptor.forClass(Hospital.class);
        verify(hospitalRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void activate_setsActiveTrue() {
        Hospital hospital = existingHospital();
        hospital.setActive(false);
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.activate("h1").isActive()).isTrue();
    }

    @Test
    void setHandlesEmergencies_noExistingEmergencyServices_createsAndEnablesIt() {
        Hospital hospital = existingHospital();
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setHandlesEmergencies("h1", true);

        ArgumentCaptor<Hospital> captor = ArgumentCaptor.forClass(Hospital.class);
        verify(hospitalRepository).save(captor.capture());
        assertThat(captor.getValue().getEmergencyServices()).isNotNull();
        assertThat(captor.getValue().getEmergencyServices().isHandlesEmergencies()).isTrue();
    }

    @Test
    void setHandlesEmergencies_existingEmergencyServices_flipsFlagAndKeepsRestOfData() {
        Hospital hospital = existingHospital();
        hospital.setEmergencyServices(EmergencyServices.builder()
                .handlesEmergencies(true)
                .specialtyEmergencyConditionsHandled(List.of("Chest Pain / Sweating / Palpitation"))
                .build());
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospital));
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setHandlesEmergencies("h1", false);

        ArgumentCaptor<Hospital> captor = ArgumentCaptor.forClass(Hospital.class);
        verify(hospitalRepository).save(captor.capture());
        EmergencyServices saved = captor.getValue().getEmergencyServices();
        assertThat(saved.isHandlesEmergencies()).isFalse();
        assertThat(saved.getSpecialtyEmergencyConditionsHandled()).containsExactly("Chest Pain / Sweating / Palpitation");
    }

    @Test
    void setHandlesEmergencies_hospitalMissing_throwsNotFound() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setHandlesEmergencies("h1", true)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findNearby_delegatesWithPointAndDistanceInKilometers() {
        when(hospitalRepository.findByAddressLocationNear(any(Point.class), any(Distance.class)))
                .thenReturn(List.of(existingHospital()));

        List<HospitalResponse> result = service.findNearby(80.2, 13.0, 5.0);

        assertThat(result).hasSize(1);
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
        ArgumentCaptor<Distance> distanceCaptor = ArgumentCaptor.forClass(Distance.class);
        verify(hospitalRepository).findByAddressLocationNear(pointCaptor.capture(), distanceCaptor.capture());
        assertThat(pointCaptor.getValue().getX()).isEqualTo(80.2);
        assertThat(pointCaptor.getValue().getY()).isEqualTo(13.0);
        assertThat(distanceCaptor.getValue().getValue()).isEqualTo(5.0);
        assertThat(distanceCaptor.getValue().getMetric()).isEqualTo(Metrics.KILOMETERS);
    }

    @Test
    void findByDistrict_trimsAndSortsByName() {
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Chennai")).thenReturn(List.of(
                Hospital.builder().id("h1").hospitalName("Zen Hospital").active(true).build(),
                Hospital.builder().id("h2").hospitalName("apollo").active(false).build()));

        List<HospitalResponse> result = service.findByDistrict(" Chennai ", null);

        assertThat(result).extracting(HospitalResponse::getId).containsExactly("h2", "h1");
    }

    @Test
    void findByDistrict_activeFilter() {
        when(hospitalRepository.findByAddressDistrictIgnoreCase("Chennai")).thenReturn(List.of(
                Hospital.builder().id("h1").hospitalName("A").active(true).build(),
                Hospital.builder().id("h2").hospitalName("B").active(false).build()));

        assertThat(service.findByDistrict("Chennai", true)).extracting(HospitalResponse::getId).containsExactly("h1");
        assertThat(service.findByDistrict("Chennai", false)).extracting(HospitalResponse::getId).containsExactly("h2");
    }

    @Test
    void findByDistrict_blank_throwsWithoutQuerying() {
        assertThatThrownBy(() -> service.findByDistrict("  ", null)).isInstanceOf(IllegalArgumentException.class);
        verify(hospitalRepository, never()).findByAddressDistrictIgnoreCase(any());
    }

    @Test
    void findNearby_negativeRadius_throwsIllegalArgumentWithoutQueryingRepository() {
        assertThatThrownBy(() -> service.findNearby(80.2, 13.0, -5.0)).isInstanceOf(IllegalArgumentException.class);

        verify(hospitalRepository, never()).findByAddressLocationNear(any(Point.class), any(Distance.class));
    }

    @Test
    void findNearby_longitudeOutOfRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.findNearby(280.0, 13.0, 5.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_latitudeOutOfRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.findNearby(80.2, 130.0, 5.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
