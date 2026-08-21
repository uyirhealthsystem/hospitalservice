package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import com.uyir.hospital.service.HospitalService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HospitalController.class)
@AutoConfigureMockMvc(addFilters = false)
class HospitalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private HospitalService hospitalService;

    private HospitalRequest validRequest() {
        return HospitalRequest.builder()
                .hospitalName("City Care Hospital")
                .registrationNumber("REG-123")
                .ownershipType(OwnershipType.PRIVATE)
                .hospitalType(HospitalType.HOSPITAL)
                .address(Address.builder()
                        .addressLine("1 Main St")
                        .city("Chennai")
                        .state("TN")
                        .country("India")
                        .pincode("600001")
                        .build())
                .contactDetails(ContactDetails.builder().phone("9999999999").build())
                .build();
    }

    private HospitalResponse response(String id) {
        return HospitalResponse.builder()
                .id(id)
                .hospitalName("City Care Hospital")
                .registrationNumber("REG-123")
                .active(true)
                .build();
    }

    @Test
    void create_validRequest_returns201WithLocation() throws Exception {
        when(hospitalService.create(any(HospitalRequest.class))).thenReturn(response("h1"));

        mockMvc.perform(post("/api/hospitals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("h1"));
    }

    @Test
    void create_blankRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/hospitals")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void create_duplicateRegistration_returns409() throws Exception {
        when(hospitalService.create(any(HospitalRequest.class)))
                .thenThrow(new DuplicateResourceException("Hospital with registration number 'REG-123' already exists"));

        mockMvc.perform(post("/api/hospitals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void getById_found_returns200() throws Exception {
        when(hospitalService.getById("h1")).thenReturn(response("h1"));

        mockMvc.perform(get("/api/hospitals/h1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("h1"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(hospitalService.getById("missing"))
                .thenThrow(new ResourceNotFoundException("Hospital not found with id 'missing'"));

        mockMvc.perform(get("/api/hospitals/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void search_withQueryParams_passesThemThrough() throws Exception {
        when(hospitalService.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(response("h1")), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/hospitals")
                        .param("city", "Chennai")
                        .param("state", "TN")
                        .param("hospitalType", "HOSPITAL")
                        .param("ownershipType", "PRIVATE")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(hospitalService).search(
                eq("Chennai"), eq("TN"), eq(HospitalType.HOSPITAL), eq(OwnershipType.PRIVATE), eq(true), any(Pageable.class));
    }

    @Test
    void search_invalidEnumValue_returns400() throws Exception {
        mockMvc.perform(get("/api/hospitals").param("hospitalType", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findNearby_missingRequiredParam_returns400() throws Exception {
        mockMvc.perform(get("/api/hospitals/nearby").param("longitude", "80.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'latitude' is missing"));
    }

    @Test
    void findNearby_validParams_delegatesToService() throws Exception {
        when(hospitalService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(response("h1")));

        mockMvc.perform(get("/api/hospitals/nearby")
                        .param("longitude", "80.2")
                        .param("latitude", "13.0")
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(hospitalService).findNearby(80.2, 13.0, 5.0);
    }

    @Test
    void findNearby_defaultRadius_isTenKm() throws Exception {
        when(hospitalService.findNearby(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        mockMvc.perform(get("/api/hospitals/nearby")
                        .param("longitude", "80.2")
                        .param("latitude", "13.0"))
                .andExpect(status().isOk());

        verify(hospitalService).findNearby(80.2, 13.0, 10.0);
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(hospitalService.update(eq("missing"), any(HospitalRequest.class)))
                .thenThrow(new ResourceNotFoundException("Hospital not found with id 'missing'"));

        mockMvc.perform(put("/api/hospitals/missing")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void activate_returns200() throws Exception {
        when(hospitalService.activate("h1")).thenReturn(response("h1"));

        mockMvc.perform(patch("/api/hospitals/h1/activate"))
                .andExpect(status().isOk());
    }

    @Test
    void deactivate_returns204() throws Exception {
        mockMvc.perform(delete("/api/hospitals/h1"))
                .andExpect(status().isNoContent());

        verify(hospitalService).deactivate("h1");
    }
}
