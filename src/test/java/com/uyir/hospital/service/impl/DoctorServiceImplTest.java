package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.DoctorRequest;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.dto.DoctorSearchRequest;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.model.enums.EngagementType;
import com.uyir.hospital.model.enums.Sex;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.DoctorSearchCriteria;
import com.uyir.hospital.repository.HospitalRepository;
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

@ExtendWith(MockitoExtension.class)
class DoctorServiceImplTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    private final DoctorMapper doctorMapper = new DoctorMapper();

    private DoctorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DoctorServiceImpl(doctorRepository, hospitalRepository, doctorMapper);
    }

    private DoctorRequest.DoctorRequestBuilder validRequestBuilder() {
        return DoctorRequest.builder()
                .name("Dr. Anita Rao")
                .sex(Sex.FEMALE)
                .tnmcNumber("TNMC-123")
                .engagementType(EngagementType.REGULAR)
                .contactDetails(com.uyir.hospital.model.embedded.ContactDetails.builder()
                        .phone("9999999999")
                        .build());
    }

    private Doctor existingDoctor() {
        return Doctor.builder()
                .id("d1")
                .name("Dr. Anita Rao")
                .tnmcNumber("TNMC-123")
                .active(true)
                .hospitalAssociations(List.of(HospitalAssociation.builder().hospitalId("h1").build()))
                .build();
    }

    @Test
    void create_newTnmc_savesAsActiveDoctor() {
        when(doctorRepository.existsByTnmcNumber("TNMC-123")).thenReturn(false);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = service.create(validRequestBuilder().build());

        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void create_duplicateTnmc_throwsAndNeverSaves() {
        when(doctorRepository.existsByTnmcNumber("TNMC-123")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validRequestBuilder().build()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(doctorRepository, never()).save(any());
    }

    @Test
    void getById_found_mapsToResponse() {
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(existingDoctor()));

        DoctorResponse response = service.getById("d1");

        assertThat(response.getId()).isEqualTo("d1");
    }

    @Test
    void getById_notFound_throws() {
        when(doctorRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_mapsEveryDoctor() {
        when(doctorRepository.findAll()).thenReturn(List.of(existingDoctor(), existingDoctor()));

        assertThat(service.getAll()).hasSize(2);
    }

    @Test
    void search_buildsCriteriaFromRequestAndDelegates() {
        DoctorSearchRequest request = DoctorSearchRequest.builder()
                .specialty("Cardiology")
                .hospitalId("h1")
                .active(true)
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(doctorRepository.search(any(DoctorSearchCriteria.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(existingDoctor()), pageable, 1));

        var result = service.search(request, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ArgumentCaptor<DoctorSearchCriteria> captor = ArgumentCaptor.forClass(DoctorSearchCriteria.class);
        verify(doctorRepository).search(captor.capture(), eq(pageable));
        assertThat(captor.getValue().getSpecialty()).isEqualTo("Cardiology");
        assertThat(captor.getValue().getHospitalId()).isEqualTo("h1");
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void update_tnmcUnchanged_skipsDuplicateCheck() {
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(existingDoctor()));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update("d1", validRequestBuilder().tnmcNumber("TNMC-123").build());

        verify(doctorRepository, never()).existsByTnmcNumber(any());
    }

    @Test
    void update_tnmcChangedToExisting_throwsDuplicate() {
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(existingDoctor()));
        when(doctorRepository.existsByTnmcNumber("TNMC-999")).thenReturn(true);

        assertThatThrownBy(() -> service.update("d1", validRequestBuilder().tnmcNumber("TNMC-999").build()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(doctorRepository, never()).save(any());
    }

    @Test
    void update_notFound_throws() {
        when(doctorRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", validRequestBuilder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivate_clearsActiveAndCheckInState() {
        Doctor doctor = existingDoctor();
        doctor.setCurrentHospitalId("h1");
        doctor.setCheckedInAt(java.time.Instant.now());
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate("d1");

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().getCurrentHospitalId()).isNull();
        assertThat(captor.getValue().getCheckedInAt()).isNull();
    }

    @Test
    void activate_setsActiveTrue() {
        Doctor doctor = existingDoctor();
        doctor.setActive(false);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = service.activate("d1");

        assertThat(response.isActive()).isTrue();
    }

    @Test
    void checkIn_activeDoctorAssociatedWithHospital_setsCurrentHospital() {
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(existingDoctor()));
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = service.checkIn("d1", "h1");

        assertThat(response.getCurrentHospitalId()).isEqualTo("h1");
        assertThat(response.getCheckedInAt()).isNotNull();
    }

    @Test
    void checkIn_inactiveDoctor_throwsIllegalArgument() {
        Doctor doctor = existingDoctor();
        doctor.setActive(false);
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.checkIn("d1", "h1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkIn_hospitalDoesNotExist_throwsResourceNotFound() {
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(existingDoctor()));
        when(hospitalRepository.existsById("h1")).thenReturn(false);

        assertThatThrownBy(() -> service.checkIn("d1", "h1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checkIn_doctorNotAssociatedWithHospital_throwsIllegalArgument() {
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(existingDoctor()));
        when(hospitalRepository.existsById("h2")).thenReturn(true);

        assertThatThrownBy(() -> service.checkIn("d1", "h2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkOut_clearsCurrentHospital() {
        Doctor doctor = existingDoctor();
        doctor.setCurrentHospitalId("h1");
        doctor.setCheckedInAt(java.time.Instant.now());
        when(doctorRepository.findById("d1")).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = service.checkOut("d1");

        assertThat(response.getCurrentHospitalId()).isNull();
        assertThat(response.getCheckedInAt()).isNull();
    }
}
