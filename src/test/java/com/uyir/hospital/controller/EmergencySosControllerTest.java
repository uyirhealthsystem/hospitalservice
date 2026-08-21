package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.EmergencyHospitalSuggestion;
import com.uyir.hospital.service.EmergencySosService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmergencySosController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmergencySosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmergencySosService emergencySosService;

    @Test
    void find_validParams_returns200WithSuggestions() throws Exception {
        when(emergencySosService.findAvailableHospitals(anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(EmergencyHospitalSuggestion.builder()
                        .hospitalId("h1")
                        .hospitalName("City Care Hospital")
                        .availableDoctorCount(2)
                        .build()));

        mockMvc.perform(get("/api/hospital/emergency-sos")
                        .param("emergencyType", "Cardiac Arrest")
                        .param("longitude", "80.2")
                        .param("latitude", "13.0")
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hospitalId").value("h1"));

        verify(emergencySosService).findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 5.0);
    }

    @Test
    void find_defaultRadius_isTenKm() throws Exception {
        when(emergencySosService.findAvailableHospitals(anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/hospital/emergency-sos")
                        .param("emergencyType", "Cardiac Arrest")
                        .param("longitude", "80.2")
                        .param("latitude", "13.0"))
                .andExpect(status().isOk());

        verify(emergencySosService).findAvailableHospitals("Cardiac Arrest", 80.2, 13.0, 10.0);
    }

    @Test
    void find_missingEmergencyType_returns400() throws Exception {
        mockMvc.perform(get("/api/hospital/emergency-sos")
                        .param("longitude", "80.2")
                        .param("latitude", "13.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'emergencyType' is missing"));
    }
}
