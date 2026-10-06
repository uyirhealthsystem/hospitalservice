package com.uyir.hospital.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uyir.hospital.dto.CancelAppointmentRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.dto.UpdateAppointmentStatusRequest;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.Sex;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.DoctorAppointmentBookingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DoctorAppointmentBookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class DoctorAppointmentBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private DoctorAppointmentBookingService doctorAppointmentBookingService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    private final Instant future = Instant.now().plus(2, ChronoUnit.DAYS);

    private DoctorAppointmentBookingRequest validRequest() {
        return DoctorAppointmentBookingRequest.builder()
                .hospitalId("h1")
                .doctorId("d1")
                .appointmentDateTime(future)
                .patientName("John Doe")
                .patientPhone("9999999999")
                .patientAge(34)
                .patientGender(Sex.MALE)
                .build();
    }

    private DoctorAppointmentBookingResponse response(String id, AppointmentStatus status) {
        return DoctorAppointmentBookingResponse.builder()
                .id(id)
                .patientId("p1")
                .hospitalId("h1")
                .doctorId("d1")
                .appointmentDateTime(future)
                .status(status)
                .build();
    }

    @Test
    void create_asPatient_returns201WithLocation() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(doctorAppointmentBookingService.create(eq("p1"), any(DoctorAppointmentBookingRequest.class)))
                .thenReturn(response("a1", AppointmentStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/hospital/doctor-appointments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("a1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void create_notPatientRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT))
                .thenThrow(new ForbiddenException("This action requires the 'PATIENT' role"));

        mockMvc.perform(post("/api/v1/hospital/doctor-appointments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyBookings_asHospital_returnsOwnBookings() throws Exception {
        when(currentUserContext.requireRole(Role.HOSPITAL)).thenReturn("h1");
        when(doctorAppointmentBookingService.getByHospitalId("h1"))
                .thenReturn(List.of(response("a1", AppointmentStatus.CONFIRMED)));

        mockMvc.perform(get("/api/v1/hospital/doctor-appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("a1"));
    }

    @Test
    void getMyHistory_asPatient_returnsOwnBookings() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        when(doctorAppointmentBookingService.getByPatientId("p1"))
                .thenReturn(List.of(response("a1", AppointmentStatus.CONFIRMED)));

        mockMvc.perform(get("/api/v1/hospital/doctor-appointments/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("a1"));
    }

    @Test
    void getMyHistory_notPatientRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT))
                .thenThrow(new ForbiddenException("This action requires the 'PATIENT' role"));

        mockMvc.perform(get("/api/v1/hospital/doctor-appointments/history")).andExpect(status().isForbidden());
    }

    @Test
    void reschedule_asHospital_returns200WithRescheduledStatus() throws Exception {
        when(currentUserContext.requireRole(Role.HOSPITAL)).thenReturn("h1");
        when(doctorAppointmentBookingService.reschedule(
                        eq("a1"), eq("h1"), any(RescheduleAppointmentRequest.class)))
                .thenReturn(response("a1", AppointmentStatus.RESCHEDULED));

        mockMvc.perform(patch("/api/v1/hospital/doctor-appointments/a1/reschedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                RescheduleAppointmentRequest.builder().appointmentDateTime(future).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULED"));
    }

    @Test
    void create_missingPatientAge_returns400() throws Exception {
        when(currentUserContext.requireRole(Role.PATIENT)).thenReturn("p1");
        DoctorAppointmentBookingRequest request = validRequest();
        request.setPatientAge(null);

        mockMvc.perform(post("/api/v1/hospital/doctor-appointments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_asPatient_passesRoleToService() throws Exception {
        when(currentUserContext.requireAnyRole(Role.PATIENT, Role.HOSPITAL)).thenReturn("p1");
        when(currentUserContext.getRole()).thenReturn(Role.PATIENT);
        when(doctorAppointmentBookingService.cancel(
                        eq("a1"), eq("p1"), eq(Role.PATIENT), any(CancelAppointmentRequest.class)))
                .thenReturn(response("a1", AppointmentStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/hospital/doctor-appointments/a1/cancel")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                CancelAppointmentRequest.builder().reason("Feeling better").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_missingReason_returns400() throws Exception {
        when(currentUserContext.requireAnyRole(Role.PATIENT, Role.HOSPITAL)).thenReturn("p1");

        mockMvc.perform(patch("/api/v1/hospital/doctor-appointments/a1/cancel")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_asHospital_returns200() throws Exception {
        when(currentUserContext.requireRole(Role.HOSPITAL)).thenReturn("h1");
        when(doctorAppointmentBookingService.updateStatus(
                        eq("a1"), eq("h1"), any(UpdateAppointmentStatusRequest.class)))
                .thenReturn(response("a1", AppointmentStatus.NO_SHOW));

        mockMvc.perform(patch("/api/v1/hospital/doctor-appointments/a1/status")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(UpdateAppointmentStatusRequest.builder()
                                .status(AppointmentStatus.NO_SHOW)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void updateStatus_notHospitalRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.HOSPITAL))
                .thenThrow(new ForbiddenException("This action requires the 'HOSPITAL' role"));

        mockMvc.perform(patch("/api/v1/hospital/doctor-appointments/a1/status")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(UpdateAppointmentStatusRequest.builder()
                                .status(AppointmentStatus.COMPLETED)
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    void reschedule_notHospitalRole_returns403() throws Exception {
        when(currentUserContext.requireRole(Role.HOSPITAL))
                .thenThrow(new ForbiddenException("This action requires the 'HOSPITAL' role"));

        mockMvc.perform(patch("/api/v1/hospital/doctor-appointments/a1/reschedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                RescheduleAppointmentRequest.builder().appointmentDateTime(future).build())))
                .andExpect(status().isForbidden());
    }
}
