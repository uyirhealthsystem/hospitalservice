package com.uyir.hospital.controller;

import com.uyir.hospital.dto.ClaimHospitalRequest;
import com.uyir.hospital.dto.EmergencyServicesToggleRequest;
import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalClaimService;
import com.uyir.hospital.service.HospitalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/hospital")
@RequiredArgsConstructor
@Tag(name = "Hospitals")
public class HospitalController {

    private final HospitalService hospitalService;
    private final HospitalClaimService hospitalClaimService;
    private final CurrentUserContext currentUserContext;

    // A HOSPITAL account creates its own hospital (its X-User-Id becomes the hospital's userId), or a PRO
    // onboards one on the hospital's behalf: the hospital is then unowned, and its id plus a one-time claim
    // code are emailed to contactDetails.email so its HOSPITAL account can claim it (POST /claim).
    @PostMapping
    public ResponseEntity<HospitalResponse> create(@Valid @RequestBody HospitalRequest request) {
        String userId = currentUserContext.requireAnyRole(Role.HOSPITAL, Role.PRO);
        HospitalResponse created = currentUserContext.getRole() == Role.PRO
                ? hospitalClaimService.onboard(userId, request)
                : hospitalService.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // A HOSPITAL account links itself to the hospital a PRO onboarded, with the hospital id and the claim
    // code from the email. Afterwards GET /me returns that hospital.
    @PostMapping("/claim")
    public HospitalResponse claim(@Valid @RequestBody ClaimHospitalRequest request) {
        String userId = currentUserContext.requireRole(Role.HOSPITAL);
        return hospitalClaimService.claim(userId, request);
    }

    // Emails a new claim code (the previous one stops working). The onboarding PRO, an ADMIN or a SUPER_ADMIN.
    @PostMapping("/{id}/claim-code/resend")
    public HospitalResponse resendClaimCode(@PathVariable String id) {
        String userId = currentUserContext.requireAnyRole(Role.PRO, Role.ADMIN, Role.SUPER_ADMIN);
        return hospitalClaimService.resendCode(userId, currentUserContext.getRole(), id);
    }

    @GetMapping("/{id}")
    public HospitalResponse getById(@PathVariable String id) {
        return hospitalService.getById(id);
    }

    // The logged-in HOSPITAL caller's own profile: resolved from X-User-Id (Hospital.userId), never a path/query
    // param. 404 if that user hasn't created a hospital yet.
    @GetMapping("/me")
    public HospitalResponse getMyHospital() {
        String userId = currentUserContext.requireRole(Role.HOSPITAL);
        return hospitalService.getByUserId(userId);
    }

    @GetMapping
    public PageResponse<HospitalResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) HospitalType hospitalType,
            @RequestParam(required = false) OwnershipType ownershipType,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "hospitalName") Pageable pageable) {
        return hospitalService.search(city, state, hospitalType, ownershipType, active, pageable);
    }

    @GetMapping("/nearby")
    public List<HospitalResponse> findNearby(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(defaultValue = "10") double radiusKm) {
        return hospitalService.findNearby(longitude, latitude, radiusKm);
    }

    // All hospitals in a district (address.district, case-insensitive), sorted by name.
    // Optional ?active=true|false; omit it to get both.
    @GetMapping("/district/{district}")
    public List<HospitalResponse> findByDistrict(
            @PathVariable String district, @RequestParam(required = false) Boolean active) {
        return hospitalService.findByDistrict(district, active);
    }

    @PutMapping("/{id}")
    public HospitalResponse update(@PathVariable String id, @Valid @RequestBody HospitalRequest request) {
        return hospitalService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    public HospitalResponse activate(@PathVariable String id) {
        return hospitalService.activate(id);
    }

    // Lets a hospital enable/disable its own emergency-sos visibility. Turning this off
    // immediately excludes the hospital from GET /api/v1/hospital/emergency-sos results.
    @PatchMapping("/{id}/emergency-services/toggle")
    public HospitalResponse toggleEmergencyServices(
            @PathVariable String id, @Valid @RequestBody EmergencyServicesToggleRequest request) {
        return hospitalService.setHandlesEmergencies(id, request.getHandlesEmergencies());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        hospitalService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
