package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.InternalDataResponse;
import com.uyir.hospital.service.InternalDataService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalDataController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalDataControllerTest {

    private static final String URL = "/api/v1/hospital/internal/lookup";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalDataService internalDataService;

    @Test
    void lookup_noKeyNeeded_returnsOnlyRequestedSections() throws Exception {
        when(internalDataService.lookup(any())).thenReturn(InternalDataResponse.builder()
                .hospitalId("h1")
                .hospital(Map.of("hospitalName", "City Care"))
                .build());

        mockMvc.perform(post(URL).contentType("application/json")
                        .content("{\"hospitalId\":\"h1\",\"include\":[\"HOSPITAL\"],\"hospitalFields\":[\"hospitalName\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospital.hospitalName").value("City Care"))
                .andExpect(jsonPath("$.doctor").doesNotExist())
                .andExpect(jsonPath("$.seats").doesNotExist());
    }

    @Test
    void lookup_unknownSection_is400() throws Exception {
        mockMvc.perform(post(URL).contentType("application/json")
                        .content("{\"hospitalId\":\"h1\",\"include\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest());
    }
}
