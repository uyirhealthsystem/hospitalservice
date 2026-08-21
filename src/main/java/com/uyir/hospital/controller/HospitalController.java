package com.uyir.hospital.controller;

import com.uyir.hospital.dto.HospitalRequest;
import com.uyir.hospital.dto.HospitalResponse;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
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
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
@Tag(name = "Hospitals")
public class HospitalController {

    private final HospitalService hospitalService;

    @PostMapping
    public ResponseEntity<HospitalResponse> create(@Valid @RequestBody HospitalRequest request) {
        HospitalResponse created = hospitalService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public HospitalResponse getById(@PathVariable String id) {
        return hospitalService.getById(id);
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

    @PutMapping("/{id}")
    public HospitalResponse update(@PathVariable String id, @Valid @RequestBody HospitalRequest request) {
        return hospitalService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    public HospitalResponse activate(@PathVariable String id) {
        return hospitalService.activate(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        hospitalService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
