package com.uyir.hospital.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.dto.AnalyticsPinResetResponse;
import com.uyir.hospital.dto.AnalyticsPinStatusResponse;
import com.uyir.hospital.exception.AnalyticsPinException;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.service.AnalyticsPinService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AnalyticsPinController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsPinControllerTest {

    private static final String BASE = "/api/v1/hospital/analytics-pin";
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsPinService analyticsPinService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(BASE + path).contentType("application/json").content(json));
    }

    @Test
    void status_returnsPinSetFlag() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(analyticsPinService.getStatus("h1")).thenReturn(new AnalyticsPinStatusResponse(true, NOW, null));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinSet").value(true))
                .andExpect(jsonPath("$.pinUpdatedAt").exists());
    }

    @Test
    void setPin_returns201() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(analyticsPinService.setPin("h1", "4821")).thenReturn(new AnalyticsPinStatusResponse(true, NOW, null));

        postJson("", "{\"pin\":\"4821\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.pinSet").value(true));
    }

    @Test
    void setPin_missingPin_returns400() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");

        postJson("", "{}").andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsPinService);
    }

    @Test
    void setPin_alreadySet_returns409WithCode() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(analyticsPinService.setPin("h1", "4821")).thenThrow(
                new AnalyticsPinException(AnalyticsPinException.Code.ANALYTICS_PIN_ALREADY_SET, "already set"));

        postJson("", "{\"pin\":\"4821\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYTICS_PIN_ALREADY_SET"));
    }

    @Test
    void setPin_notHospital_returns403() throws Exception {
        when(currentUserContext.requireHospitalId()).thenThrow(new ForbiddenException("HOSPITAL role required"));

        postJson("", "{\"pin\":\"4821\"}").andExpect(status().isForbidden());
        verifyNoInteractions(analyticsPinService);
    }

    @Test
    void changePin_passesBothPins() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(analyticsPinService.changePin("h1", "4821", "7395")).thenReturn(new AnalyticsPinStatusResponse(true, NOW, null));

        postJson("/change", "{\"currentPin\":\"4821\",\"newPin\":\"7395\"}").andExpect(status().isOk());
    }

    @Test
    void changePin_missingNewPin_returns400() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");

        postJson("/change", "{\"currentPin\":\"4821\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsPinService);
    }

    @Test
    void requestReset_returnsMaskedEmail() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(analyticsPinService.requestReset("h1"))
                .thenReturn(new AnalyticsPinResetResponse("ad***@city.in", NOW.plusSeconds(900)));

        mockMvc.perform(post(BASE + "/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeSentTo").value("ad***@city.in"));
    }

    @Test
    void confirmReset_passesCodeAndNewPin() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(analyticsPinService.confirmReset("h1", "482913", "7395"))
                .thenReturn(new AnalyticsPinStatusResponse(true, NOW, null));

        postJson("/reset/confirm", "{\"resetCode\":\"482913\",\"newPin\":\"7395\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinSet").value(true));
    }
}
