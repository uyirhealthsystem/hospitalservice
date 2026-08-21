package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.HospitalMapper;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import com.uyir.hospital.repository.HospitalRepository;
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

@ExtendWith(MockitoExtension.class)
class HospitalServiceImplTest {

    @Mock
    private HospitalRepository hospitalRepository;

    private final HospitalMapper hospitalMapper = new HospitalMapper();

    private HospitalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HospitalServiceImpl(hospitalRepository, hospitalMapper);
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
        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));

        HospitalResponse response = service.create(validRequestBuilder().build());

        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void create_duplicateRegistrationNumber_throwsAndNeverSaves() {
        when(hospitalRepository.existsByRegistrationNumber("REG-123")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validRequestBuilder().build()))
                .isInstanceOf(DuplicateResourceException.class);

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
}
