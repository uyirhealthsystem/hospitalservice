package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.EmergencyBookingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmergencyBookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmergencyBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private EmergencyBookingService emergencyBookingService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    private EmergencyBookingRequest validRequest() {
        return EmergencyBookingRequest.builder()
                .hospitalId("h1")
                .emergencyType("Cardiac Arrest")
                .patientName("John Doe")
                .patientPhone("9999999999")
                .build();
    }

    private EmergencyBookingResponse response(String id) {
        return EmergencyBookingResponse.builder()
                .id(id)
                .patientId("p1")
                .hospitalId("h1")
                .emergencyType("Cardiac Arrest")
                .status(EmergencyBookingStatus.REQUESTED)
                .build();
    }

    @Test
    void create_asPatient_returns201WithLocation() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(emergencyBookingService.create(eq("p1"), any(EmergencyBookingRequest.class))).thenReturn(response("b1"));

        mockMvc.perform(post("/api/hospital/emergency-bookings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("b1"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void create_notPatientRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT))
                .thenThrow(new ForbiddenException("This action requires the 'PATIENT' role"));

        mockMvc.perform(post("/api/hospital/emergency-bookings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_blankRequest_returns400() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");

        mockMvc.perform(post("/api/hospital/emergency-bookings")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyBookings_asHospital_returnsOwnBookings() throws Exception {
        when(currentUserContext.requireRole(Role.HOSPITAL)).thenReturn("h1");
        when(emergencyBookingService.getByHospitalId("h1")).thenReturn(List.of(response("b1")));

        mockMvc.perform(get("/api/hospital/emergency-bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("b1"));
    }

    @Test
    void getMyBookings_notHospitalRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.HOSPITAL))
                .thenThrow(new ForbiddenException("This action requires the 'HOSPITAL' role"));

        mockMvc.perform(get("/api/hospital/emergency-bookings")).andExpect(status().isForbidden());
    }

    @Test
    void getMyHistory_asPatient_returnsOwnBookings() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(emergencyBookingService.getByPatientId("p1")).thenReturn(List.of(response("b1")));

        mockMvc.perform(get("/api/hospital/emergency-bookings/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("b1"));
    }

    @Test
    void getMyHistory_notPatientRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT))
                .thenThrow(new ForbiddenException("This action requires the 'PATIENT' role"));

        mockMvc.perform(get("/api/hospital/emergency-bookings/history")).andExpect(status().isForbidden());
    }
}
