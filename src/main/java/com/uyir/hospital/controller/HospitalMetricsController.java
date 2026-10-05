package com.uyir.hospital.controller;

import com.uyir.hospital.dto.DistrictAnalyticsReport;
import com.uyir.hospital.dto.DistrictMetricsRequest;
import com.uyir.hospital.dto.HospitalMetricsRequest;
import com.uyir.hospital.dto.HospitalMetricsResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalAnalyticsService;
import com.uyir.hospital.service.HospitalMetricsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Business metrics, service-wide or for one district. ADMIN / SUPER_ADMIN only. Like the analytics
// endpoints the counts are POST with the data in the body; the service-wide one accepts an empty body.
// The exception is GET /{district}, the all-in-one district report, which takes the district in the URL.
@RestController
@RequestMapping("/api/v1/hospital/metrics")
@RequiredArgsConstructor
@Tag(name = "Hospital Metrics")
public class HospitalMetricsController {

    private final HospitalMetricsService metricsService;
    private final HospitalAnalyticsService analyticsService;
    private final CurrentUserContext currentUserContext;

    @PostMapping
    public HospitalMetricsResponse getMetrics(@RequestBody(required = false) HospitalMetricsRequest request) {
        currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
        HospitalMetricsRequest range = request != null ? request : new HospitalMetricsRequest();
        return metricsService.getMetrics(range.getFromDate(), range.getToDate());
    }

    // Same counts scoped to one district. As with the analytics, the Admin service decides which
    // district an admin may see and passes it in; this service trusts it.
    @PostMapping("/district")
    public HospitalMetricsResponse getDistrictMetrics(@Valid @RequestBody DistrictMetricsRequest request) {
        currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
        return metricsService.getDistrictMetrics(
                request.getDistrict().trim(), request.getFromDate(), request.getToDate());
    }

    // District in the path, no date params (always the default last 30 days).
    // Returns summary + per-hospital breakdown + daily trends in one response.
    @GetMapping("/{district}")
    public DistrictAnalyticsReport getDistrictReport(@PathVariable String district) {
        currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
        if (district.isBlank()) {
            throw new IllegalArgumentException("district must not be blank");
        }
        return analyticsService.getDistrictReport(district.trim());
    }
}
