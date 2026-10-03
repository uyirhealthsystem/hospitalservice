package com.uyir.hospital.controller;

import com.uyir.hospital.dto.DistrictMatricsRequest;
import com.uyir.hospital.dto.HospitalMatricsRequest;
import com.uyir.hospital.dto.HospitalMatricsResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalMatricsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Business matrics, service-wide or for one district. ADMIN / SUPER_ADMIN only. Like the analytics
// endpoints everything is POST with the data in the body; the service-wide one accepts an empty body.
@RestController
@RequestMapping("/api/hospital/matrics")
@RequiredArgsConstructor
@Tag(name = "Hospital Matrics")
public class HospitalMatricsController {

    private final HospitalMatricsService matricsService;
    private final CurrentUserContext currentUserContext;

    @PostMapping
    public HospitalMatricsResponse getMatrics(@RequestBody(required = false) HospitalMatricsRequest request) {
        currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
        HospitalMatricsRequest range = request != null ? request : new HospitalMatricsRequest();
        return matricsService.getMatrics(range.getFromDate(), range.getToDate());
    }

    // Same counts scoped to one district. As with the analytics, the Admin service decides which
    // district an admin may see and passes it in; this service trusts it.
    @PostMapping("/district")
    public HospitalMatricsResponse getDistrictMatrics(@Valid @RequestBody DistrictMatricsRequest request) {
        currentUserContext.requireAnyRole(Role.ADMIN, Role.SUPER_ADMIN);
        return matricsService.getDistrictMatrics(
                request.getDistrict().trim(), request.getFromDate(), request.getToDate());
    }
}
