package com.uyir.hospital.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.AnalyticsSnapshotRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotResponse;
import com.uyir.hospital.dto.AnalyticsTrendPoint;
import com.uyir.hospital.dto.DistrictAnalyticsSummary;
import com.uyir.hospital.dto.HospitalAnalyticsResponse;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalAnalyticsService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HospitalAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class HospitalAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HospitalAnalyticsService analyticsService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    @BeforeEach
    void asAdmin() {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN)).thenReturn("admin1");
    }

    @Test
    void getSummary_passesDistrictAndDates() throws Exception {
        when(analyticsService.getSummary("Chennai", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(DistrictAnalyticsSummary.builder().district("Chennai").build());

        mockMvc.perform(get("/api/hospital/analytics/summary")
                        .param("district", "Chennai")
                        .param("fromDate", "2026-09-01")
                        .param("toDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.district").value("Chennai"));
    }

    @Test
    void getSummary_withoutDates_passesNulls() throws Exception {
        when(analyticsService.getSummary(eq("Chennai"), isNull(), isNull()))
                .thenReturn(DistrictAnalyticsSummary.builder().district("Chennai").build());

        mockMvc.perform(get("/api/hospital/analytics/summary").param("district", " Chennai "))
                .andExpect(status().isOk());
    }

    @Test
    void getSummary_missingDistrict_returns400() throws Exception {
        mockMvc.perform(get("/api/hospital/analytics/summary")).andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void getSummary_blankDistrict_returns400() throws Exception {
        mockMvc.perform(get("/api/hospital/analytics/summary").param("district", "  "))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void getSummary_notAdmin_returns403() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN))
                .thenThrow(new ForbiddenException("This action requires one of the roles [ADMIN, SUPER_ADMIN]"));

        mockMvc.perform(get("/api/hospital/analytics/summary").param("district", "Chennai"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void getSummary_malformedDate_returns400() throws Exception {
        mockMvc.perform(get("/api/hospital/analytics/summary")
                        .param("district", "Chennai")
                        .param("fromDate", "01-09-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSummary_invalidRange_returns400() throws Exception {
        when(analyticsService.getSummary(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("fromDate must be on or before toDate"));

        mockMvc.perform(get("/api/hospital/analytics/summary")
                        .param("district", "Chennai")
                        .param("fromDate", "2026-09-30")
                        .param("toDate", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHospitalBreakdown_returnsRows() throws Exception {
        when(analyticsService.getHospitalBreakdown(eq("Chennai"), isNull(), isNull()))
                .thenReturn(List.of(HospitalAnalyticsResponse.builder().hospitalId("h1").build()));

        mockMvc.perform(get("/api/hospital/analytics/hospitals").param("district", "Chennai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hospitalId").value("h1"));
    }

    @Test
    void getHospitalAnalytics_otherDistrict_returns403() throws Exception {
        when(analyticsService.getHospitalAnalytics(eq("Chennai"), eq("h9"), isNull(), isNull()))
                .thenThrow(new ForbiddenException("Hospital 'h9' is not in district 'Chennai'"));

        mockMvc.perform(get("/api/hospital/analytics/hospitals/h9").param("district", "Chennai"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTrends_returnsPoints() throws Exception {
        when(analyticsService.getTrends(eq("Chennai"), isNull(), isNull()))
                .thenReturn(List.of(AnalyticsTrendPoint.builder()
                        .date(LocalDate.of(2026, 9, 1))
                        .emergencyBookings(3)
                        .build()));

        mockMvc.perform(get("/api/hospital/analytics/trends").param("district", "Chennai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$[0].emergencyBookings").value(3));
    }

    @Test
    void createSnapshot_returns201WithLocationWithoutQuery() throws Exception {
        when(analyticsService.createSnapshot(eq("Chennai"), eq("admin1"), any(AnalyticsSnapshotRequest.class)))
                .thenReturn(AnalyticsSnapshotResponse.builder().id("s1").district("Chennai").build());

        mockMvc.perform(post("/api/hospital/analytics/snapshots")
                        .param("district", "Chennai")
                        .contentType("application/json")
                        .content("{\"label\":\"September review\",\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/hospital/analytics/snapshots/s1")))
                .andExpect(jsonPath("$.id").value("s1"));
    }

    @Test
    void createSnapshot_labelTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/hospital/analytics/snapshots")
                        .param("district", "Chennai")
                        .contentType("application/json")
                        .content("{\"label\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(analyticsService, never()).createSnapshot(any(), any(), any());
    }

    @Test
    void getSnapshots_returnsList() throws Exception {
        when(analyticsService.getSnapshots("Chennai"))
                .thenReturn(List.of(AnalyticsSnapshotResponse.builder().id("s1").build()));

        mockMvc.perform(get("/api/hospital/analytics/snapshots").param("district", "Chennai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"));
    }

    @Test
    void deleteSnapshot_returns204() throws Exception {
        mockMvc.perform(delete("/api/hospital/analytics/snapshots/s1").param("district", "Chennai"))
                .andExpect(status().isNoContent());
        verify(analyticsService).deleteSnapshot("Chennai", "s1");
    }
}
