package com.uyir.hospital.controller;

import com.uyir.hospital.dto.DistrictMetricsRequest;
import com.uyir.hospital.dto.HospitalMetricsRequest;
import com.uyir.hospital.dto.HospitalMetricsResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalMetricsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Business metrics, service-wide or for one district. ADMIN / SUPER_ADMIN only. Like the analytics
// endpoints everything is POST with the data in the body; the service-wide one accepts an empty body.
@RestController
@RequestMapping("/api/hospital/metrics")
@RequiredArgsConstructor
@Tag(name = "Hospital Metrics")
public class HospitalMetricsController {

    private final HospitalMetricsService metricsService;
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
}
