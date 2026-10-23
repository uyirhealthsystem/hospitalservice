package com.uyir.hospital.controller;

import com.uyir.hospital.catalog.EmergencyCondition;
import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hospital/emergency-conditions")
@RequiredArgsConstructor
@Tag(name = "Emergency Conditions")
public class EmergencyConditionController {

    private final EmergencyConditionCatalog emergencyConditionCatalog;

    // Public: the 16 emergency conditions - the patient picks one in SOS (its code goes in emergencyType), and the
    // hospital and doctor forms use the same list to say which emergencies they handle.
    @GetMapping
    public List<EmergencyCondition> getAll() {
        return emergencyConditionCatalog.getConditions();
    }
}
