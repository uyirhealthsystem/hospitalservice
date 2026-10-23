package com.uyir.hospital.controller;

import com.uyir.hospital.dto.EmergencyBookingReasonRequest;
import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.event.EmergencyAlertHub;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.EmergencyBookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/hospital/emergency-bookings")
@RequiredArgsConstructor
@Tag(name = "Emergency Bookings")
public class EmergencyBookingController {

    private final EmergencyBookingService emergencyBookingService;
    private final CurrentUserContext currentUserContext;
    private final EmergencyAlertHub emergencyAlertHub;

    // Patient-only: books an emergency slot at a hospital.
    @PostMapping
    public ResponseEntity<EmergencyBookingResponse> create(@Valid @RequestBody EmergencyBookingRequest request) {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        EmergencyBookingResponse created = emergencyBookingService.create(patientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Hospital-only: the logged-in hospital's own emergency bookings.
    @GetMapping
    public List<EmergencyBookingResponse> getMyBookings() {
        String hospitalId = currentUserContext.requireHospitalId();
        return emergencyBookingService.getByHospitalId(hospitalId);
    }

    // Hospital-only: live feed (Server-Sent Events) of new emergency bookings for the logged-in hospital.
    // Each arrives as an "emergency-booking-requested" event; a comment ping every 10s keeps it open.
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return emergencyAlertHub.subscribe(currentUserContext.requireHospitalId());
    }

    // Hospital-only: takes the emergency on. REQUESTED -> ACCEPTED.
    @PatchMapping("/{id}/accept")
    public EmergencyBookingResponse accept(@PathVariable String id) {
        return emergencyBookingService.accept(id, currentUserContext.requireHospitalId());
    }

    // Hospital-only: turns the emergency down, with a reason. REQUESTED -> REJECTED.
    @PatchMapping("/{id}/reject")
    public EmergencyBookingResponse reject(
            @PathVariable String id, @Valid @RequestBody EmergencyBookingReasonRequest request) {
        return emergencyBookingService.reject(id, currentUserContext.requireHospitalId(), request.getReason());
    }

    // Hospital-only: closes out an accepted emergency. ACCEPTED -> COMPLETED.
    @PatchMapping("/{id}/complete")
    public EmergencyBookingResponse complete(@PathVariable String id) {
        return emergencyBookingService.complete(id, currentUserContext.requireHospitalId());
    }

    // Patient-only: withdraws the patient's own request, with a reason. REQUESTED / ACCEPTED -> CANCELLED.
    @PatchMapping("/{id}/cancel")
    public EmergencyBookingResponse cancel(
            @PathVariable String id, @Valid @RequestBody EmergencyBookingReasonRequest request) {
        return emergencyBookingService.cancel(id, currentUserContext.requireRole(Role.PATIENT), request.getReason());
    }

    // Patient-only: the logged-in patient's own emergency booking history.
    @GetMapping("/history")
    public List<EmergencyBookingResponse> getMyHistory() {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        return emergencyBookingService.getByPatientId(patientId);
    }
}
