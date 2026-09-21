package com.uyir.hospital.controller;

import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.EmergencyBookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/hospital/emergency-bookings")
@RequiredArgsConstructor
@Tag(name = "Emergency Bookings")
public class EmergencyBookingController {

    private final EmergencyBookingService emergencyBookingService;
    private final CurrentUserContext currentUserContext;

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
        String hospitalId = currentUserContext.requireRole(Role.HOSPITAL);
        return emergencyBookingService.getByHospitalId(hospitalId);
    }

    // Patient-only: the logged-in patient's own emergency booking history.
    @GetMapping("/history")
    public List<EmergencyBookingResponse> getMyHistory() {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        return emergencyBookingService.getByPatientId(patientId);
    }
}
