package com.uyir.hospital.service;

import com.uyir.hospital.dto.EmergencyHospitalSuggestion;
import java.util.List;

public interface EmergencySosService {

    List<EmergencyHospitalSuggestion> findAvailableHospitals(
            String emergencyType, double longitude, double latitude, double radiusKm);
}
