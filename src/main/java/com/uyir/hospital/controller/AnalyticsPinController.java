package com.uyir.hospital.controller;

import com.uyir.hospital.dto.AnalyticsPinResetResponse;
import com.uyir.hospital.dto.AnalyticsPinStatusResponse;
import com.uyir.hospital.dto.ChangeAnalyticsPinRequest;
import com.uyir.hospital.dto.ConfirmAnalyticsPinResetRequest;
import com.uyir.hospital.dto.SetAnalyticsPinRequest;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.service.AnalyticsPinService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// HOSPITAL only, the caller's own hospital. The PIN guards POST /analytics/my-hospital and
// POST /metrics/my-hospital, which take it in the body on every call.
@RestController
@RequestMapping("/api/v1/hospital/analytics-pin")
@RequiredArgsConstructor
@Tag(name = "Analytics PIN")
public class AnalyticsPinController {

    private final AnalyticsPinService analyticsPinService;
    private final CurrentUserContext currentUserContext;

    @GetMapping
    public AnalyticsPinStatusResponse getStatus() {
        return analyticsPinService.getStatus(currentUserContext.requireHospitalId());
    }

    @PostMapping
    public ResponseEntity<AnalyticsPinStatusResponse> setPin(@Valid @RequestBody SetAnalyticsPinRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return ResponseEntity.status(HttpStatus.CREATED).body(analyticsPinService.setPin(hospitalId, request.pin()));
    }

    @PostMapping("/change")
    public AnalyticsPinStatusResponse changePin(@Valid @RequestBody ChangeAnalyticsPinRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return analyticsPinService.changePin(hospitalId, request.currentPin(), request.newPin());
    }

    // Forgotten PIN: emails a 6-digit code (valid 15 minutes) to the hospital's contact email.
    @PostMapping("/reset")
    public AnalyticsPinResetResponse requestReset() {
        return analyticsPinService.requestReset(currentUserContext.requireHospitalId());
    }

    @PostMapping("/reset/confirm")
    public AnalyticsPinStatusResponse confirmReset(@Valid @RequestBody ConfirmAnalyticsPinResetRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return analyticsPinService.confirmReset(hospitalId, request.resetCode(), request.newPin());
    }
}
