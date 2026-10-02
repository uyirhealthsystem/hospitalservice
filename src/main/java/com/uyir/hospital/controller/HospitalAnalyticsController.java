package com.uyir.hospital.controller;

import com.uyir.hospital.dto.AnalyticsSnapshotRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotResponse;
import com.uyir.hospital.dto.AnalyticsTrendPoint;
import com.uyir.hospital.dto.DistrictAnalyticsSummary;
import com.uyir.hospital.dto.HospitalAnalyticsResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalAnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// ADMIN / SUPER_ADMIN only. The Admin service owns which district an admin manages and passes
// it as the `district` param - this service doesn't look admins up, it just scopes to it.
@RestController
@RequestMapping("/api/hospital/analytics")
@RequiredArgsConstructor
@Tag(name = "Hospital Analytics")
public class HospitalAnalyticsController {

    private final HospitalAnalyticsService analyticsService;
    private final CurrentUserContext currentUserContext;

    @GetMapping("/summary")
    public DistrictAnalyticsSummary getSummary(
            @RequestParam String district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        requireAdmin();
        return analyticsService.getSummary(normalize(district), fromDate, toDate);
    }

    @GetMapping("/hospitals")
    public List<HospitalAnalyticsResponse> getHospitalBreakdown(
            @RequestParam String district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        requireAdmin();
        return analyticsService.getHospitalBreakdown(normalize(district), fromDate, toDate);
    }

    @GetMapping("/hospitals/{hospitalId}")
    public HospitalAnalyticsResponse getHospitalAnalytics(
            @PathVariable String hospitalId,
            @RequestParam String district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        requireAdmin();
        return analyticsService.getHospitalAnalytics(normalize(district), hospitalId, fromDate, toDate);
    }

    @GetMapping("/trends")
    public List<AnalyticsTrendPoint> getTrends(
            @RequestParam String district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        requireAdmin();
        return analyticsService.getTrends(normalize(district), fromDate, toDate);
    }

    // Saves a frozen copy of the district summary for the given range.
    @PostMapping("/snapshots")
    public ResponseEntity<AnalyticsSnapshotResponse> createSnapshot(
            @RequestParam String district, @Valid @RequestBody AnalyticsSnapshotRequest request) {
        String adminId = requireAdmin();
        AnalyticsSnapshotResponse created = analyticsService.createSnapshot(normalize(district), adminId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQuery(null)
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/snapshots")
    public List<AnalyticsSnapshotResponse> getSnapshots(@RequestParam String district) {
        requireAdmin();
        return analyticsService.getSnapshots(normalize(district));
    }

    @GetMapping("/snapshots/{id}")
    public AnalyticsSnapshotResponse getSnapshot(@PathVariable String id, @RequestParam String district) {
        requireAdmin();
        return analyticsService.getSnapshot(normalize(district), id);
    }

    @DeleteMapping("/snapshots/{id}")
    public ResponseEntity<Void> deleteSnapshot(@PathVariable String id, @RequestParam String district) {
        requireAdmin();
        analyticsService.deleteSnapshot(normalize(district), id);
        return ResponseEntity.noContent().build();
    }

    private String requireAdmin() {
        return currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
    }

    // `?district=` (present but empty) passes @RequestParam binding, so reject it explicitly.
    private static String normalize(String district) {
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'district' must not be blank");
        }
        return district.trim();
    }
}
