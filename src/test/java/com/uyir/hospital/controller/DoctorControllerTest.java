package com.uyir.hospital.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uyir.hospital.dto.DoctorCheckInRequest;
import com.uyir.hospital.dto.DoctorRequest;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.dto.DoctorSearchRequest;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.exception.DuplicateResourceException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.enums.EngagementType;
import com.uyir.hospital.model.enums.Sex;
import com.uyir.hospital.service.DoctorService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DoctorController.class)
@AutoConfigureMockMvc(addFilters = false)
class DoctorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DoctorService doctorService;

    private DoctorRequest validRequest() {
        return DoctorRequest.builder()
                .name("Dr. Anita Rao")
                .age(40)
                .sex(Sex.FEMALE)
                .tnmcNumber("TNMC-123")
                .engagementType(EngagementType.REGULAR)
                .contactDetails(ContactDetails.builder().phone("9999999999").build())
                .build();
    }

    private DoctorResponse response(String id) {
        return DoctorResponse.builder()
                .id(id)
                .name("Dr. Anita Rao")
                .sex(Sex.FEMALE)
                .tnmcNumber("TNMC-123")
                .engagementType(EngagementType.REGULAR)
                .active(true)
                .build();
    }

    @Test
    void create_validRequest_returns201WithLocation() throws Exception {
        when(doctorService.create(any(DoctorRequest.class))).thenReturn(response("d1"));

        mockMvc.perform(post("/api/hospital/doctors")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/hospital/doctors/d1")))
                .andExpect(jsonPath("$.id").value("d1"));
    }

    @Test
    void create_blankRequest_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/hospital/doctors")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details", hasItem("name: must not be blank")))
                .andExpect(jsonPath("$.details", hasItem("sex: must not be null")))
                .andExpect(jsonPath("$.details", hasItem("tnmcNumber: must not be blank")))
                .andExpect(jsonPath("$.details", hasItem("engagementType: must not be null")))
                .andExpect(jsonPath("$.details", hasItem("contactDetails: must not be null")));
    }

    @Test
    void getById_found_returns200() throws Exception {
        when(doctorService.getById("d1")).thenReturn(response("d1"));

        mockMvc.perform(get("/api/hospital/doctors/d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("d1"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(doctorService.getById("missing")).thenThrow(new ResourceNotFoundException("Doctor not found with id 'missing'"));

        mockMvc.perform(get("/api/hospital/doctors/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Doctor not found with id 'missing'"));
    }

    @Test
    void list_returnsAllDoctors() throws Exception {
        when(doctorService.getAll()).thenReturn(List.of(response("d1"), response("d2")));

        mockMvc.perform(post("/api/hospital/doctors/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void search_emptyBody_delegatesWithBlankCriteria() throws Exception {
        when(doctorService.search(any(DoctorSearchRequest.class), any()))
                .thenReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                        List.of(response("d1")), PageRequest.of(0, 20), 1)));

        mockMvc.perform(post("/api/hospital/doctors/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("d1"));

        var captor = org.mockito.ArgumentCaptor.forClass(DoctorSearchRequest.class);
        verify(doctorService).search(captor.capture(), any());
        DoctorSearchRequest captured = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(captured.getSpecialty()).isNull();
        org.assertj.core.api.Assertions.assertThat(captured.getHospitalId()).isNull();
        org.assertj.core.api.Assertions.assertThat(captured.getActive()).isNull();
    }

    @Test
    void search_withFilters_passesThemThrough() throws Exception {
        when(doctorService.search(any(DoctorSearchRequest.class), any()))
                .thenReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                        List.of(), PageRequest.of(0, 20), 0)));

        DoctorSearchRequest request = DoctorSearchRequest.builder()
                .specialty("Cardiology")
                .hospitalId("h1")
                .active(true)
                .build();

        mockMvc.perform(post("/api/hospital/doctors/search")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(DoctorSearchRequest.class);
        verify(doctorService).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSpecialty()).isEqualTo("Cardiology");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getHospitalId()).isEqualTo("h1");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(doctorService.update(eq("missing"), any(DoctorRequest.class)))
                .thenThrow(new ResourceNotFoundException("Doctor not found with id 'missing'"));

        mockMvc.perform(put("/api/hospital/doctors/missing")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_duplicateTnmc_returns409() throws Exception {
        when(doctorService.update(eq("d1"), any(DoctorRequest.class)))
                .thenThrow(new DuplicateResourceException("Doctor with TNMC number 'TNMC-123' already exists"));

        mockMvc.perform(put("/api/hospital/doctors/d1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void activate_returns200() throws Exception {
        when(doctorService.activate("d1")).thenReturn(response("d1"));

        mockMvc.perform(patch("/api/hospital/doctors/d1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("d1"));
    }

    @Test
    void deactivate_returns204() throws Exception {
        mockMvc.perform(delete("/api/hospital/doctors/d1"))
                .andExpect(status().isNoContent());

        verify(doctorService).deactivate("d1");
    }

    @Test
    void checkIn_validRequest_returns200() throws Exception {
        when(doctorService.checkIn(eq("d1"), eq("h1"))).thenReturn(response("d1"));

        mockMvc.perform(post("/api/hospital/doctors/d1/check-in")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                DoctorCheckInRequest.builder().hospitalId("h1").build())))
                .andExpect(status().isOk());
    }

    @Test
    void checkIn_blankHospitalId_returns400() throws Exception {
        mockMvc.perform(post("/api/hospital/doctors/d1/check-in")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("hospitalId: must not be blank")));
    }

    @Test
    void checkIn_inactiveDoctor_returns400() throws Exception {
        when(doctorService.checkIn(eq("d1"), eq("h1")))
                .thenThrow(new IllegalArgumentException("Cannot check in an inactive doctor"));

        mockMvc.perform(post("/api/hospital/doctors/d1/check-in")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                DoctorCheckInRequest.builder().hospitalId("h1").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot check in an inactive doctor"));
    }

    @Test
    void checkOut_returns200() throws Exception {
        when(doctorService.checkOut("d1")).thenReturn(response("d1"));

        mockMvc.perform(post("/api/hospital/doctors/d1/check-out"))
                .andExpect(status().isOk());
    }

    @Test
    void wrongHttpMethodOnCollectionPath_returns405() throws Exception {
        mockMvc.perform(get("/api/hospital/doctors"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void genuinelyUnmappedPath_returns404() throws Exception {
        mockMvc.perform(get("/api/hospital/doctors/d1/nonexistent/segment"))
                .andExpect(status().isNotFound());
    }
}
