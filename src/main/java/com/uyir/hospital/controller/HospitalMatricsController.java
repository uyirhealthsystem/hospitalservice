package com.uyir.hospital.controller;

import com.uyir.hospital.dto.HospitalMatricsRequest;
import com.uyir.hospital.dto.HospitalMatricsResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.HospitalMatricsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Service-wide (all districts) business matrics, ADMIN / SUPER_ADMIN only. Like the analytics
// endpoints it's POST with the optional date range in the body; an empty body is allowed.
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
}
