package com.uyir.hospital.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.DoctorTokenLimitResponse;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.service.DoctorTokenLimitService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DoctorTokenLimitController.class)
@AutoConfigureMockMvc(addFilters = false)
class DoctorTokenLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorTokenLimitService doctorTokenLimitService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    private DoctorTokenLimitResponse response(Integer daily, Map<String, Integer> dates) {
        return DoctorTokenLimitResponse.builder()
                .hospitalId("h1")
                .doctorId("d1")
                .dailyLimit(daily)
                .dateLimits(dates)
                .build();
    }

    @Test
    void getMyLimits_asHospital_returnsList() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(doctorTokenLimitService.getForHospital("h1")).thenReturn(List.of(response(30, Map.of())));

        mockMvc.perform(get("/api/v1/hospital/token-limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dailyLimit").value(30));
    }

    @Test
    void setDailyLimit_asHospital_returnsUpdated() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(doctorTokenLimitService.setDailyLimit("h1", "d1", 30)).thenReturn(response(30, Map.of()));

        mockMvc.perform(put("/api/v1/hospital/token-limits/d1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimit\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(30));
    }

    @Test
    void setDailyLimit_zero_returns400() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");

        mockMvc.perform(put("/api/v1/hospital/token-limits/d1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimit\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setDailyLimit_notHospital_returns403() throws Exception {
        when(currentUserContext.requireHospitalId()).thenThrow(new ForbiddenException("nope"));

        mockMvc.perform(put("/api/v1/hospital/token-limits/d1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimit\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setDateLimit_asHospital_returnsUpdated() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 5);
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(doctorTokenLimitService.setDateLimit("h1", "d1", date, 0))
                .thenReturn(response(30, Map.of("2026-11-05", 0)));

        mockMvc.perform(put("/api/v1/hospital/token-limits/d1/dates/2026-11-05")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateLimits['2026-11-05']").value(0));
    }

    @Test
    void setDateLimit_missingLimit_returns400() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");

        mockMvc.perform(put("/api/v1/hospital/token-limits/d1/dates/2026-11-05")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeDateLimit_asHospital_returnsUpdated() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(doctorTokenLimitService.removeDateLimit("h1", "d1", LocalDate.of(2026, 11, 5)))
                .thenReturn(response(30, Map.of()));

        mockMvc.perform(delete("/api/v1/hospital/token-limits/d1/dates/2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(30));
    }
}
