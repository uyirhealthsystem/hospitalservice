package com.uyir.hospital.controller;

import com.uyir.hospital.dto.AnalyticsRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotListRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotLookupRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotResponse;
import com.uyir.hospital.dto.AnalyticsTrendPoint;
import com.uyir.hospital.dto.DistrictAnalyticsSummary;
import com.uyir.hospital.dto.HospitalAnalyticsRequest;
import com.uyir.hospital.dto.HospitalAnalyticsResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalAnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ADMIN / SUPER_ADMIN only. The Admin service owns which district an admin manages and passes
// it in the request body - this service doesn't look admins up, it just scopes to it.
// Every endpoint is POST with a JSON body (same approach as DoctorController's /list and
// /search) so no filter data travels in the URL. The all-in-one GET report lives in
// HospitalMetricsController as GET /api/hospital/metrics/{district}.
@RestController
@RequestMapping("/api/hospital/analytics")
@RequiredArgsConstructor
@Tag(name = "Hospital Analytics")
public class HospitalAnalyticsController {

    private final HospitalAnalyticsService analyticsService;
    private final CurrentUserContext currentUserContext;

    @PostMapping("/summary")
    public DistrictAnalyticsSummary getSummary(@Valid @RequestBody AnalyticsRequest request) {
        requireAdmin();
        return analyticsService.getSummary(request.getDistrict().trim(), request.getFromDate(), request.getToDate());
    }

    @PostMapping("/hospitals")
    public List<HospitalAnalyticsResponse> getHospitalBreakdown(@Valid @RequestBody AnalyticsRequest request) {
        requireAdmin();
        return analyticsService.getHospitalBreakdown(
                request.getDistrict().trim(), request.getFromDate(), request.getToDate());
    }

    @PostMapping("/hospitals/detail")
    public HospitalAnalyticsResponse getHospitalAnalytics(@Valid @RequestBody HospitalAnalyticsRequest request) {
        requireAdmin();
        return analyticsService.getHospitalAnalytics(
                request.getDistrict().trim(), request.getHospitalId(), request.getFromDate(), request.getToDate());
    }

    @PostMapping("/trends")
    public List<AnalyticsTrendPoint> getTrends(@Valid @RequestBody AnalyticsRequest request) {
        requireAdmin();
        return analyticsService.getTrends(request.getDistrict().trim(), request.getFromDate(), request.getToDate());
    }

    // Saves a frozen copy of the district summary for the given range.
    @PostMapping("/snapshots")
    public ResponseEntity<AnalyticsSnapshotResponse> createSnapshot(@Valid @RequestBody AnalyticsSnapshotRequest request) {
        String adminId = requireAdmin();
        AnalyticsSnapshotResponse created =
                analyticsService.createSnapshot(request.getDistrict().trim(), adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/snapshots/list")
    public List<AnalyticsSnapshotResponse> getSnapshots(@Valid @RequestBody AnalyticsSnapshotListRequest request) {
        requireAdmin();
        return analyticsService.getSnapshots(request.getDistrict().trim());
    }

    @PostMapping("/snapshots/detail")
    public AnalyticsSnapshotResponse getSnapshot(@Valid @RequestBody AnalyticsSnapshotLookupRequest request) {
        requireAdmin();
        return analyticsService.getSnapshot(request.getDistrict().trim(), request.getSnapshotId());
    }

    // POST rather than DELETE: DELETE request bodies are dropped by many proxies and clients.
    @PostMapping("/snapshots/delete")
    public ResponseEntity<Void> deleteSnapshot(@Valid @RequestBody AnalyticsSnapshotLookupRequest request) {
        requireAdmin();
        analyticsService.deleteSnapshot(request.getDistrict().trim(), request.getSnapshotId());
        return ResponseEntity.noContent().build();
    }

    private String requireAdmin() {
        return currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
    }
}
