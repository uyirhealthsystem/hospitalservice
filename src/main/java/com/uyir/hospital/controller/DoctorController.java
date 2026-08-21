package com.uyir.hospital.controller;

import com.uyir.hospital.dto.DoctorCheckInRequest;
import com.uyir.hospital.dto.DoctorRequest;
import com.uyir.hospital.dto.DoctorResponse;
import com.uyir.hospital.dto.DoctorSearchRequest;
import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.service.DoctorService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/hospital/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors")
public class DoctorController {

    private final DoctorService doctorService;

    @PostMapping
    public ResponseEntity<DoctorResponse> create(@Valid @RequestBody DoctorRequest request) {
        DoctorResponse created = doctorService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public DoctorResponse getById(@PathVariable String id) {
        return doctorService.getById(id);
    }

    @PostMapping("/list")
    public List<DoctorResponse> getAllDoctors() {
        return doctorService.getAll();
    }

    @PostMapping("/search")
    public PageResponse<DoctorResponse> search(
            @RequestBody(required = false) DoctorSearchRequest request,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return doctorService.search(request != null ? request : new DoctorSearchRequest(), pageable);
    }

    @PutMapping("/{id}")
    public DoctorResponse update(@PathVariable String id, @Valid @RequestBody DoctorRequest request) {
        return doctorService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    public DoctorResponse activate(@PathVariable String id) {
        return doctorService.activate(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        doctorService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/check-in")
    public DoctorResponse checkIn(@PathVariable String id, @Valid @RequestBody DoctorCheckInRequest request) {
        return doctorService.checkIn(id, request.getHospitalId());
    }

    @PostMapping("/{id}/check-out")
    public DoctorResponse checkOut(@PathVariable String id) {
        return doctorService.checkOut(id);
    }
}
