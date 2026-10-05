package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(HospitalAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class HospitalAnalyticsControllerTest {

    private static final String BASE = "/api/v1/hospital/analytics";

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

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(BASE + path).contentType("application/json").content(json));
    }

    @Test
    void summary_passesDistrictAndDatesFromBody() throws Exception {
        when(analyticsService.getSummary("Chennai", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(DistrictAnalyticsSummary.builder().district("Chennai").build());

        postJson("/summary", "{\"district\":\"Chennai\",\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-30\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.district").value("Chennai"));
    }

    @Test
    void summary_withoutDates_passesNullsAndTrimsDistrict() throws Exception {
        when(analyticsService.getSummary(eq("Chennai"), isNull(), isNull()))
                .thenReturn(DistrictAnalyticsSummary.builder().district("Chennai").build());

        postJson("/summary", "{\"district\":\" Chennai \"}").andExpect(status().isOk());
    }

    @Test
    void summary_missingDistrict_returns400() throws Exception {
        postJson("/summary", "{}").andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void summary_blankDistrict_returns400() throws Exception {
        postJson("/summary", "{\"district\":\"  \"}").andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void summary_noBody_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/summary").contentType("application/json")).andExpect(status().isBadRequest());
    }

    @Test
    void summary_malformedDate_returns400() throws Exception {
        postJson("/summary", "{\"district\":\"Chennai\",\"fromDate\":\"01-09-2026\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    void summary_viaGet_isNotAllowed() throws Exception {
        mockMvc.perform(get(BASE + "/summary")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void summary_notAdmin_returns403() throws Exception {
        when(currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN))
                .thenThrow(new ForbiddenException("This action requires one of the roles [ADMIN, SUPER_ADMIN]"));

        postJson("/summary", "{\"district\":\"Chennai\"}").andExpect(status().isForbidden());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void summary_invalidRange_returns400() throws Exception {
        when(analyticsService.getSummary(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("fromDate must be on or before toDate"));

        postJson("/summary", "{\"district\":\"Chennai\",\"fromDate\":\"2026-09-30\",\"toDate\":\"2026-09-01\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void hospitals_returnsRows() throws Exception {
        when(analyticsService.getHospitalBreakdown(eq("Chennai"), isNull(), isNull()))
                .thenReturn(List.of(HospitalAnalyticsResponse.builder().hospitalId("h1").build()));

        postJson("/hospitals", "{\"district\":\"Chennai\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hospitalId").value("h1"));
    }

    @Test
    void hospitalDetail_readsHospitalIdFromBody() throws Exception {
        when(analyticsService.getHospitalAnalytics(eq("Chennai"), eq("h1"), isNull(), isNull()))
                .thenReturn(HospitalAnalyticsResponse.builder().hospitalId("h1").build());

        postJson("/hospitals/detail", "{\"district\":\"Chennai\",\"hospitalId\":\"h1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalId").value("h1"));
    }

    @Test
    void hospitalDetail_missingHospitalId_returns400() throws Exception {
        postJson("/hospitals/detail", "{\"district\":\"Chennai\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void hospitalDetail_otherDistrict_returns403() throws Exception {
        when(analyticsService.getHospitalAnalytics(eq("Chennai"), eq("h9"), isNull(), isNull()))
                .thenThrow(new ForbiddenException("Hospital 'h9' is not in district 'Chennai'"));

        postJson("/hospitals/detail", "{\"district\":\"Chennai\",\"hospitalId\":\"h9\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void trends_returnsPoints() throws Exception {
        when(analyticsService.getTrends(eq("Chennai"), isNull(), isNull()))
                .thenReturn(List.of(AnalyticsTrendPoint.builder()
                        .date(LocalDate.of(2026, 9, 1))
                        .emergencyBookings(3)
                        .build()));

        postJson("/trends", "{\"district\":\"Chennai\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$[0].emergencyBookings").value(3));
    }

    @Test
    void createSnapshot_returns201() throws Exception {
        when(analyticsService.createSnapshot(eq("Chennai"), eq("admin1"), any(AnalyticsSnapshotRequest.class)))
                .thenReturn(AnalyticsSnapshotResponse.builder().id("s1").district("Chennai").build());

        postJson("/snapshots", "{\"district\":\"Chennai\",\"label\":\"September review\",\"fromDate\":\"2026-09-01\",\"toDate\":\"2026-09-30\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s1"));
    }

    @Test
    void createSnapshot_labelTooLong_returns400() throws Exception {
        postJson("/snapshots", "{\"district\":\"Chennai\",\"label\":\"" + "x".repeat(121) + "\"}")
                .andExpect(status().isBadRequest());
        verify(analyticsService, never()).createSnapshot(any(), any(), any());
    }

    @Test
    void listSnapshots_returnsList() throws Exception {
        when(analyticsService.getSnapshots("Chennai"))
                .thenReturn(List.of(AnalyticsSnapshotResponse.builder().id("s1").build()));

        postJson("/snapshots/list", "{\"district\":\"Chennai\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"));
    }

    @Test
    void snapshotDetail_readsIdFromBody() throws Exception {
        when(analyticsService.getSnapshot("Chennai", "s1"))
                .thenReturn(AnalyticsSnapshotResponse.builder().id("s1").build());

        postJson("/snapshots/detail", "{\"district\":\"Chennai\",\"snapshotId\":\"s1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s1"));
    }

    @Test
    void deleteSnapshot_returns204() throws Exception {
        postJson("/snapshots/delete", "{\"district\":\"Chennai\",\"snapshotId\":\"s1\"}")
                .andExpect(status().isNoContent());
        verify(analyticsService).deleteSnapshot("Chennai", "s1");
    }

    @Test
    void deleteSnapshot_missingSnapshotId_returns400() throws Exception {
        postJson("/snapshots/delete", "{\"district\":\"Chennai\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsService);
    }
}
