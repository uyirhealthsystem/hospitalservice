package com.uyir.hospital.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.dto.PatientActivityItem;
import com.uyir.hospital.dto.PatientActivityResponse;
import com.uyir.hospital.dto.PatientActivitySummary;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.model.enums.PatientActivityType;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.PatientActivityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PatientActivityController.class)
@AutoConfigureMockMvc(addFilters = false)
class PatientActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientActivityService patientActivityService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    @Test
    void getMyActivity_patient_returnsFirstPageOfTimeline() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(patientActivityService.getActivity(eq("p1"), isNull(), any(Pageable.class)))
                .thenReturn(PatientActivityResponse.builder()
                        .patientId("p1")
                        .summary(PatientActivitySummary.builder().totalEmergencies(1).build())
                        .activities(PageResponse.<PatientActivityItem>builder()
                                .content(List.of(PatientActivityItem.builder()
                                        .type(PatientActivityType.EMERGENCY)
                                        .id("e1")
                                        .status("REQUESTED")
                                        .active(true)
                                        .build()))
                                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                                .build())
                        .build());

        mockMvc.perform(get("/api/v1/hospital/patients/me/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value("p1"))
                .andExpect(jsonPath("$.summary.totalEmergencies").value(1))
                .andExpect(jsonPath("$.activities.content[0].type").value("EMERGENCY"))
                .andExpect(jsonPath("$.activities.content[0].active").value(true))
                .andExpect(jsonPath("$.activities.totalElements").value(1))
                .andExpect(jsonPath("$.activities.last").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(patientActivityService).getActivity(eq("p1"), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void getMyActivity_typeAndPageParams_arePassedThrough() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(patientActivityService.getActivity(eq("p1"), eq(PatientActivityType.APPOINTMENT), any(Pageable.class)))
                .thenReturn(PatientActivityResponse.builder().patientId("p1").build());

        mockMvc.perform(get("/api/v1/hospital/patients/me/activity")
                        .param("type", "APPOINTMENT")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(patientActivityService).getActivity(eq("p1"), eq(PatientActivityType.APPOINTMENT), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getMyActivity_unknownType_returns400() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");

        mockMvc.perform(get("/api/v1/hospital/patients/me/activity").param("type", "SURGERY"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(patientActivityService);
    }

    @Test
    void getMyActivity_notAPatient_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT))
                .thenThrow(new ForbiddenException("This action requires the 'PATIENT' role"));

        mockMvc.perform(get("/api/v1/hospital/patients/me/activity"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(patientActivityService);
    }
}
