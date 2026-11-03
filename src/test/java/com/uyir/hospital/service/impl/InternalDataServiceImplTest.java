package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.DoctorHospitalStatus;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.InternalDataRequest;
import com.uyir.hospital.dto.InternalDataResponse;
import com.uyir.hospital.dto.InternalDataSection;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.service.DoctorService;
import com.uyir.hospital.service.HospitalSeatService;
import com.uyir.hospital.service.HospitalService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InternalDataServiceImplTest {

    private HospitalService hospitalService;
    private DoctorService doctorService;
    private DoctorRepository doctorRepository;
    private HospitalSeatService seatService;
    private InternalDataServiceImpl service;

    @BeforeEach
    void setUp() {
        hospitalService = mock(HospitalService.class);
        doctorService = mock(DoctorService.class);
        doctorRepository = mock(DoctorRepository.class);
        seatService = mock(HospitalSeatService.class);
        service = new InternalDataServiceImpl(hospitalService, doctorService, doctorRepository,
                new DoctorMapper(), seatService, JsonMapper.builder().build());

        when(hospitalService.getById("h1")).thenReturn(HospitalResponse.builder()
                .id("h1").hospitalName("City Care").active(true)
                .address(Address.builder().district("Madurai").city("Madurai").build())
                .build());
        when(doctorService.getById("d1")).thenReturn(DoctorResponse.builder()
                .id("d1").name("Dr Ravi").active(true).consultationFee(new BigDecimal("300"))
                .currentHospitalId("h1")
                .hospitalAssociations(List.of(HospitalAssociation.builder()
                        .hospitalId("h1").department("Cardiology").consultationFee(new BigDecimal("500"))
                        .active(true).build()))
                .build());
    }

    @Test
    void noInclude_bothIds_returnsHospitalDoctorAndStatus() {
        when(seatService.hasSeat("h1", "d1")).thenReturn(true);

        InternalDataResponse out = service.lookup(InternalDataRequest.builder().hospitalId("h1").doctorId("d1").build());

        assertThat(out.getHospital()).containsEntry("hospitalName", "City Care");
        assertThat(out.getDoctor()).containsEntry("name", "Dr Ravi");
        DoctorHospitalStatus status = out.getDoctorStatus();
        assertThat(status.isSeated()).isTrue();
        assertThat(status.isBookable()).isTrue();
        assertThat(status.isCheckedInHere()).isTrue();
        assertThat(status.getDepartment()).isEqualTo("Cardiology");
        assertThat(status.getConsultationFee()).isEqualByComparingTo("500");
        assertThat(out.getSeats()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fields_projectTopLevelAndDottedPaths() {
        InternalDataResponse out = service.lookup(InternalDataRequest.builder()
                .hospitalId("h1")
                .include(List.of(InternalDataSection.HOSPITAL))
                .hospitalFields(List.of("hospitalName", "address.district"))
                .build());

        assertThat(out.getHospital()).containsOnlyKeys("hospitalName", "address");
        assertThat((Map<String, Object>) out.getHospital().get("address")).containsOnlyKeys("district");
        assertThat(out.getDoctorStatus()).isNull();
    }

    @Test
    void unknownField_is400WithValidFields() {
        assertThatThrownBy(() -> service.lookup(InternalDataRequest.builder()
                        .hospitalId("h1").hospitalFields(List.of("nope")).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown field 'nope'")
                .hasMessageContaining("hospitalName");
    }

    @Test
    void notSeated_isNotBookable() {
        when(seatService.hasSeat("h1", "d1")).thenReturn(false);

        DoctorHospitalStatus status = service.lookup(InternalDataRequest.builder()
                .hospitalId("h1").doctorId("d1").include(List.of(InternalDataSection.DOCTOR_STATUS)).build())
                .getDoctorStatus();

        assertThat(status.isSeated()).isFalse();
        assertThat(status.isBookable()).isFalse();
    }

    @Test
    void seatedDoctors_projectedWithDoctorFields() {
        when(seatService.seatedDoctorIds("h1")).thenReturn(Set.of("d1"));
        when(doctorRepository.findAllById(Set.of("d1"))).thenReturn(List.of(
                Doctor.builder().id("d1").name("Dr Ravi").specialties(List.of("Cardiology")).active(true).build()));

        InternalDataResponse out = service.lookup(InternalDataRequest.builder()
                .hospitalId("h1")
                .include(List.of(InternalDataSection.SEATED_DOCTORS))
                .doctorFields(List.of("id", "name"))
                .build());

        assertThat(out.getSeatedDoctors()).containsExactly(Map.of("id", "d1", "name", "Dr Ravi"));
        assertThat(out.getHospital()).isNull();
    }

    @Test
    void sectionMissingItsId_is400() {
        assertThatThrownBy(() -> service.lookup(InternalDataRequest.builder()
                        .doctorId("d1").include(List.of(InternalDataSection.SEATS)).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SEATS needs hospitalId");
        verifyNoInteractions(seatService);
    }

    @Test
    void noIds_is400() {
        assertThatThrownBy(() -> service.lookup(new InternalDataRequest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownHospital_is404() {
        when(hospitalService.getById("x")).thenThrow(new ResourceNotFoundException("Hospital not found"));

        assertThatThrownBy(() -> service.lookup(InternalDataRequest.builder().hospitalId("x").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
