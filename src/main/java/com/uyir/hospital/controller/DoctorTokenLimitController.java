package com.uyir.hospital.controller;

import com.uyir.hospital.dto.DailyTokenLimitRequest;
import com.uyir.hospital.dto.DateTokenLimitRequest;
import com.uyir.hospital.dto.DoctorTokenLimitResponse;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.service.DoctorTokenLimitService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Hospital-only: how many appointment tokens the hospital issues for each of its seated doctors.
// A daily limit applies every day; a date limit overrides it for one date (0 = no appointments that day).
// Emergency bookings are not affected by these limits.
@RestController
@RequestMapping("/api/v1/hospital/token-limits")
@RequiredArgsConstructor
@Tag(name = "Appointment Token Limits")
public class DoctorTokenLimitController {

    private final DoctorTokenLimitService doctorTokenLimitService;
    private final CurrentUserContext currentUserContext;

    @GetMapping
    public List<DoctorTokenLimitResponse> getMyLimits() {
        return doctorTokenLimitService.getForHospital(currentUserContext.requireHospitalId());
    }

    @PutMapping("/{doctorId}")
    public DoctorTokenLimitResponse setDailyLimit(
            @PathVariable String doctorId, @Valid @RequestBody DailyTokenLimitRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorTokenLimitService.setDailyLimit(hospitalId, doctorId, request.getDailyLimit());
    }

    @PutMapping("/{doctorId}/dates/{date}")
    public DoctorTokenLimitResponse setDateLimit(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DateTokenLimitRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorTokenLimitService.setDateLimit(hospitalId, doctorId, date, request.getLimit());
    }

    @DeleteMapping("/{doctorId}/dates/{date}")
    public DoctorTokenLimitResponse removeDateLimit(
            @PathVariable String doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorTokenLimitService.removeDateLimit(hospitalId, doctorId, date);
    }
}
