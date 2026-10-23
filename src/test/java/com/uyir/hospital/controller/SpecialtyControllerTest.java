package com.uyir.hospital.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uyir.hospital.catalog.Specialty;
import com.uyir.hospital.catalog.SpecialtyCatalog;
import com.uyir.hospital.catalog.SpecialtyCategory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpecialtyController.class)
@AutoConfigureMockMvc(addFilters = false)
class SpecialtyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpecialtyCatalog specialtyCatalog;

    @Test
    void getAll_returnsCategoriesWithCodesAndNames_noHeadersNeeded() throws Exception {
        when(specialtyCatalog.getCategories()).thenReturn(List.of(new SpecialtyCategory("Heart & Vascular",
                List.of(new Specialty("CAR001", "Cardiology"), new Specialty("CAR002", "Interventional Cardiology")))));

        mockMvc.perform(get("/api/v1/hospital/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Heart & Vascular"))
                .andExpect(jsonPath("$[0].specialties[0].code").value("CAR001"))
                .andExpect(jsonPath("$[0].specialties[1].name").value("Interventional Cardiology"));
    }
}
