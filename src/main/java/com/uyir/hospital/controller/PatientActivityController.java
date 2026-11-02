package com.uyir.hospital.controller;

import com.uyir.hospital.dto.PatientActivityResponse;
import com.uyir.hospital.model.enums.PatientActivityType;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.PatientActivityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hospital/patients/me")
@RequiredArgsConstructor
@Tag(name = "Patient Activity")
public class PatientActivityController {

    private final PatientActivityService patientActivityService;
    private final CurrentUserContext currentUserContext;

    // Patient-only: everything the logged-in patient has done in the hospital service - emergency and
    // appointment bookings as one timeline, with a summary. ?type=EMERGENCY|APPOINTMENT narrows the list;
    // ?page (0-based) and ?size (default 20, max 100) page it. Any sort param is ignored: always newest first.
    @GetMapping("/activity")
    public PatientActivityResponse getMyActivity(
            @RequestParam(required = false) PatientActivityType type,
            @PageableDefault(size = 20) Pageable pageable) {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        return patientActivityService.getActivity(patientId, type, pageable);
    }
}
