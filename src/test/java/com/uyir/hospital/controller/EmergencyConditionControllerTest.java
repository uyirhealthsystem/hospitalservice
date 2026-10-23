package com.uyir.hospital.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.catalog.EmergencyCondition;
import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmergencyConditionController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmergencyConditionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmergencyConditionCatalog emergencyConditionCatalog;

    @Test
    void getAll_returnsConditionsWithCodeNameAndIcon_noHeadersNeeded() throws Exception {
        when(emergencyConditionCatalog.getConditions()).thenReturn(List.of(
                new EmergencyCondition("EMG002", "Stroke / Paralysis / Sudden Weakness", "🧠"),
                new EmergencyCondition("EMG016", "Other Emergency", "❗")));

        mockMvc.perform(get("/api/v1/hospital/emergency-conditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("EMG002"))
                .andExpect(jsonPath("$[0].icon").value("🧠"))
                .andExpect(jsonPath("$[1].name").value("Other Emergency"));
    }
}
