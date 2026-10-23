package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uyir.hospital.dto.EmergencyBookingReasonRequest;
import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.event.EmergencyAlertHub;
import com.uyir.hospital.exception.ConflictException;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @MockitoBean
    private EmergencyAlertHub emergencyAlertHub;

    @Test
    void stream_hospital_subscribesItsOwnHospitalToLiveAlerts() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(emergencyAlertHub.subscribe("h1")).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/hospital/emergency-bookings/stream"))
                .andExpect(request().asyncStarted());

        verify(emergencyAlertHub).subscribe("h1");
    }

    @Test
    void stream_notAHospital_returns403() throws Exception {
        when(currentUserContext.requireHospitalId())
                .thenThrow(new ForbiddenException("This action requires the 'HOSPITAL' role"));

        mockMvc.perform(get("/api/v1/hospital/emergency-bookings/stream"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(emergencyAlertHub);
    }

    private EmergencyBookingRequest validRequest() {
        return EmergencyBookingRequest.builder()
                .hospitalId("h1")
                .emergencyType("Chest Pain / Sweating / Palpitation")
                .patientName("John Doe")
                .patientPhone("9999999999")
                .build();
    }

    private EmergencyBookingResponse response(String id) {
        return EmergencyBookingResponse.builder()
                .id(id)
                .patientId("p1")
                .hospitalId("h1")
                .emergencyType("Chest Pain / Sweating / Palpitation")
                .status(EmergencyBookingStatus.REQUESTED)
                .build();
    }

    @Test
    void create_asPatient_returns201WithLocation() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(emergencyBookingService.create(eq("p1"), any(EmergencyBookingRequest.class))).thenReturn(response("b1"));

        mockMvc.perform(post("/api/v1/hospital/emergency-bookings")
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

        mockMvc.perform(post("/api/v1/hospital/emergency-bookings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_blankRequest_returns400() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");

        mockMvc.perform(post("/api/v1/hospital/emergency-bookings")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyBookings_asHospital_returnsOwnBookings() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(emergencyBookingService.getByHospitalId("h1")).thenReturn(List.of(response("b1")));

        mockMvc.perform(get("/api/v1/hospital/emergency-bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("b1"));
    }

    @Test
    void getMyBookings_notHospitalRole_returns403() throws Exception {
        when(currentUserContext.requireHospitalId())
                .thenThrow(new ForbiddenException("This action requires the 'HOSPITAL' role"));

        mockMvc.perform(get("/api/v1/hospital/emergency-bookings")).andExpect(status().isForbidden());
    }

    @Test
    void getMyHistory_asPatient_returnsOwnBookings() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(emergencyBookingService.getByPatientId("p1")).thenReturn(List.of(response("b1")));

        mockMvc.perform(get("/api/v1/hospital/emergency-bookings/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("b1"));
    }

    @Test
    void getMyHistory_notPatientRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT))
                .thenThrow(new ForbiddenException("This action requires the 'PATIENT' role"));

        mockMvc.perform(get("/api/v1/hospital/emergency-bookings/history")).andExpect(status().isForbidden());
    }

    private EmergencyBookingResponse responseWithStatus(EmergencyBookingStatus status) {
        EmergencyBookingResponse response = response("b1");
        response.setStatus(status);
        return response;
    }

    private String reasonJson(String reason) throws Exception {
        return objectMapper.writeValueAsString(EmergencyBookingReasonRequest.builder().reason(reason).build());
    }

    @Test
    void accept_asHospital_usesResolvedHospitalId() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(emergencyBookingService.accept("b1", "h1")).thenReturn(responseWithStatus(EmergencyBookingStatus.ACCEPTED));

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void accept_notHospital_returns403() throws Exception {
        when(currentUserContext.requireHospitalId())
                .thenThrow(new ForbiddenException("This action requires the 'HOSPITAL' role"));

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/accept")).andExpect(status().isForbidden());
        verifyNoInteractions(emergencyBookingService);
    }

    @Test
    void accept_wrongStatus_returns409() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(emergencyBookingService.accept("b1", "h1"))
                .thenThrow(new ConflictException("Cannot change emergency booking 'b1' from CANCELLED to ACCEPTED"));

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/accept")).andExpect(status().isConflict());
    }

    @Test
    void reject_asHospital_passesReason() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(emergencyBookingService.reject("b1", "h1", "No ICU bed free"))
                .thenReturn(responseWithStatus(EmergencyBookingStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/reject")
                        .contentType("application/json")
                        .content(reasonJson("No ICU bed free")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reject_missingReason_returns400() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/reject")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(emergencyBookingService);
    }

    @Test
    void complete_asHospital_returns200() throws Exception {
        when(currentUserContext.requireHospitalId()).thenReturn("h1");
        when(emergencyBookingService.complete("b1", "h1")).thenReturn(responseWithStatus(EmergencyBookingStatus.COMPLETED));

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void cancel_asPatient_passesPatientIdAndReason() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(emergencyBookingService.cancel("b1", "p1", "Went to a closer hospital"))
                .thenReturn(responseWithStatus(EmergencyBookingStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/cancel")
                        .contentType("application/json")
                        .content(reasonJson("Went to a closer hospital")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_notPatient_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT))
                .thenThrow(new ForbiddenException("This action requires the 'PATIENT' role"));

        mockMvc.perform(patch("/api/v1/hospital/emergency-bookings/b1/cancel")
                        .contentType("application/json")
                        .content(reasonJson("x")))
                .andExpect(status().isForbidden());
    }
}
