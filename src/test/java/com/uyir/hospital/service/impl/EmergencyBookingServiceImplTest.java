package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.EmergencyBookingMapper;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmergencyBookingServiceImplTest {

    @Mock
    private EmergencyBookingRepository emergencyBookingRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    private final EmergencyBookingMapper emergencyBookingMapper = new EmergencyBookingMapper();

    private EmergencyBookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmergencyBookingServiceImpl(emergencyBookingRepository, hospitalRepository, emergencyBookingMapper);
    }

    private EmergencyBookingRequest validRequest() {
        return EmergencyBookingRequest.builder()
                .hospitalId("h1")
                .emergencyType("Cardiac Arrest")
                .patientName("John Doe")
                .patientPhone("9999999999")
                .build();
    }

    private Hospital hospitalHandling(boolean active, String... conditions) {
        return Hospital.builder()
                .id("h1")
                .active(active)
                .emergencyServices(EmergencyServices.builder()
                        .handlesEmergencies(true)
                        .specialtyEmergencyConditionsHandled(List.of(conditions))
                        .build())
                .build();
    }

    @Test
    void create_hospitalHandlesEmergencyType_savesRequestedBookingForPatient() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Cardiac Arrest")));
        when(emergencyBookingRepository.save(org.mockito.ArgumentMatchers.any(EmergencyBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyBookingResponse response = service.create("p1", validRequest());

        assertThat(response.getPatientId()).isEqualTo("p1");
        assertThat(response.getHospitalId()).isEqualTo("h1");
        assertThat(response.getStatus()).isEqualTo(EmergencyBookingStatus.REQUESTED);

        ArgumentCaptor<EmergencyBooking> captor = ArgumentCaptor.forClass(EmergencyBooking.class);
        org.mockito.Mockito.verify(emergencyBookingRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo("p1");
        assertThat(captor.getValue().getRequestedAt()).isNotNull();
    }

    @Test
    void create_hospitalMissing_throwsNotFound() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_hospitalInactive_throwsNotFound() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(false, "Cardiac Arrest")));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_hospitalDoesNotHandleRequestedEmergencyType_throwsIllegalArgument() {
        when(hospitalRepository.findById("h1")).thenReturn(Optional.of(hospitalHandling(true, "Burns")));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_hospitalIdNeverSuggestedBySos_stillRejectedIfCapabilityMismatched() {
        // Guards against a client bypassing the emergency-sos lookup and booking directly with a
        // hospitalId that doesn't actually handle the requested emergencyType.
        when(hospitalRepository.findById("h1"))
                .thenReturn(Optional.of(Hospital.builder().id("h1").active(true).build()));

        assertThatThrownBy(() -> service.create("p1", validRequest())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByHospitalId_returnsMappedBookings() {
        EmergencyBooking booking = EmergencyBooking.builder()
                .id("b1")
                .hospitalId("h1")
                .patientId("p1")
                .status(EmergencyBookingStatus.REQUESTED)
                .build();
        when(emergencyBookingRepository.findByHospitalIdOrderByRequestedAtDesc("h1")).thenReturn(List.of(booking));

        List<EmergencyBookingResponse> result = service.getByHospitalId("h1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("b1");
    }

    @Test
    void getByPatientId_returnsMappedBookings() {
        EmergencyBooking booking = EmergencyBooking.builder()
                .id("b1")
                .hospitalId("h1")
                .patientId("p1")
                .status(EmergencyBookingStatus.REQUESTED)
                .build();
        when(emergencyBookingRepository.findByPatientIdOrderByRequestedAtDesc("p1")).thenReturn(List.of(booking));

        List<EmergencyBookingResponse> result = service.getByPatientId("p1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("b1");
    }
}
