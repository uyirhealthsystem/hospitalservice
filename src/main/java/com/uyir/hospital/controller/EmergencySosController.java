package com.uyir.hospital.controller;

import com.uyir.hospital.dto.EmergencyHospitalSuggestion;
import com.uyir.hospital.service.EmergencySosService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hospital/emergency-sos")
@RequiredArgsConstructor
@Tag(name = "Emergency SOS")
public class EmergencySosController {

    private final EmergencySosService emergencySosService;

    @GetMapping
    public List<EmergencyHospitalSuggestion> find(
            @RequestParam String emergencyType,
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(defaultValue = "10") double radiusKm) {
        return emergencySosService.findAvailableHospitals(emergencyType, longitude, latitude, radiusKm);
    }
}
