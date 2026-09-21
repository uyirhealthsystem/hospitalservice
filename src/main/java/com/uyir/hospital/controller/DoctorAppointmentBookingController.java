package com.uyir.hospital.controller;

import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.DoctorAppointmentBookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/hospital/doctor-appointments")
@RequiredArgsConstructor
@Tag(name = "Doctor Appointment Bookings")
public class DoctorAppointmentBookingController {

    private final DoctorAppointmentBookingService doctorAppointmentBookingService;
    private final CurrentUserContext currentUserContext;

    // Patient-only: books an appointment with a doctor at a hospital.
    @PostMapping
    public ResponseEntity<DoctorAppointmentBookingResponse> create(
            @Valid @RequestBody DoctorAppointmentBookingRequest request) {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        DoctorAppointmentBookingResponse created = doctorAppointmentBookingService.create(patientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Hospital-only: the logged-in hospital's own appointment bookings.
    @GetMapping
    public List<DoctorAppointmentBookingResponse> getMyBookings() {
        String hospitalId = currentUserContext.requireRole(Role.HOSPITAL);
        return doctorAppointmentBookingService.getByHospitalId(hospitalId);
    }

    // Patient-only: the logged-in patient's own appointment booking history.
    @GetMapping("/history")
    public List<DoctorAppointmentBookingResponse> getMyHistory() {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        return doctorAppointmentBookingService.getByPatientId(patientId);
    }

    // Hospital-only: appointment bookings can be rescheduled; emergency bookings cannot.
    @PatchMapping("/{id}/reschedule")
    public DoctorAppointmentBookingResponse reschedule(
            @PathVariable String id, @Valid @RequestBody RescheduleAppointmentRequest request) {
        String hospitalId = currentUserContext.requireRole(Role.HOSPITAL);
        return doctorAppointmentBookingService.reschedule(id, hospitalId, request);
    }
}
