package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.HospitalMetricsResponse;
import com.uyir.hospital.dto.HospitalMetricsResponse.HospitalMetrics;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalMetricsService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HospitalMetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
class HospitalMetricsControllerTest {

    private static final String BASE = "/api/hospital/metrics";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HospitalMetricsService metricsService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    @BeforeEach
    void asAdmin() {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");
    }

    @Test
    void metrics_passesDatesFromBody() throws Exception {
        when(metricsService.getMetrics(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(HospitalMetricsResponse.builder()
                        .hospitals(HospitalMetrics.builder().total(5).build())
                        .build());

        mockMvc.perform(post(BASE).contentType("application/json")
                        .content("{\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitals.total").value(5));
    }

    @Test
    void metrics_emptyJsonBody_passesNullDates() throws Exception {
        when(metricsService.getMetrics(isNull(), isNull())).thenReturn(new HospitalMetricsResponse());

        mockMvc.perform(post(BASE).contentType("application/json").content("{}")).andExpect(status().isOk());
    }

    @Test
    void metrics_noBody_passesNullDates() throws Exception {
        when(metricsService.getMetrics(isNull(), isNull())).thenReturn(new HospitalMetricsResponse());

        mockMvc.perform(post(BASE)).andExpect(status().isOk());
    }

    @Test
    void metrics_malformedDate_returns400() throws Exception {
        mockMvc.perform(post(BASE).contentType("application/json").content("{\"fromDate\":\"01-09-2026\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(metricsService);
    }

    @Test
    void metrics_invalidRange_returns400() throws Exception {
        when(metricsService.getMetrics(any(), any()))
                .thenThrow(new IllegalArgumentException("fromDate must be on or before toDate"));

        mockMvc.perform(post(BASE).contentType("application/json")
                        .content("{\"fromDate\":\"2026-09-30\",\"toDate\":\"2026-09-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metrics_notAdmin_returns403() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN))
                .thenThrow(new ForbiddenException("This action requires one of the roles [ADMIN, SUPER_ADMIN]"));

        mockMvc.perform(post(BASE).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(metricsService);
    }

    @Test
    void metrics_viaGet_isNotAllowed() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void districtMetrics_passesTrimmedDistrictAndDates() throws Exception {
        when(metricsService.getDistrictMetrics("Chennai", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(HospitalMetricsResponse.builder().district("Chennai").build());

        mockMvc.perform(post(BASE + "/district").contentType("application/json")
                        .content("{\"district\":\" Chennai \",\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.district").value("Chennai"));
    }

    @Test
    void districtMetrics_missingDistrict_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/district").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(metricsService);
    }

    @Test
    void districtMetrics_noBody_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/district").contentType("application/json")).andExpect(status().isBadRequest());
        verifyNoInteractions(metricsService);
    }

    @Test
    void districtMetrics_notAdmin_returns403() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN))
                .thenThrow(new ForbiddenException("This action requires one of the roles [ADMIN, SUPER_ADMIN]"));

        mockMvc.perform(post(BASE + "/district").contentType("application/json").content("{\"district\":\"Chennai\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(metricsService);
    }

    @Test
    void serviceWideMetrics_omitsDistrictField() throws Exception {
        when(metricsService.getMetrics(isNull(), isNull())).thenReturn(new HospitalMetricsResponse());

        mockMvc.perform(post(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.district").doesNotExist());
    }
}
