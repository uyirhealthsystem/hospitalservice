package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.EmergencyHospitalSuggestion;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.EmergencySosService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmergencySosServiceImpl implements EmergencySosService {

    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;

    @Override
    public List<EmergencyHospitalSuggestion> findAvailableHospitals(
            String emergencyType, double longitude, double latitude, double radiusKm) {

        Point point = new Point(longitude, latitude);
        Distance distance = new Distance(radiusKm, Metrics.KILOMETERS);

        // findByAddressLocationNear compiles to a MongoDB $near query, which returns
        // results nearest-first - no separate distance sort/computation needed here.
        return hospitalRepository.findByAddressLocationNear(point, distance).stream()
                .filter(Hospital::isActive)
                .filter(hospital -> handlesEmergency(hospital, emergencyType))
                .map(this::toSuggestion)
                .filter(suggestion -> suggestion.getAvailableDoctorCount() > 0)
                .toList();
    }

    private boolean handlesEmergency(Hospital hospital, String emergencyType) {
        EmergencyServices emergencyServices = hospital.getEmergencyServices();
        if (emergencyServices == null || !emergencyServices.isHandlesEmergencies()) {
            return false;
        }
        List<String> handled = emergencyServices.getSpecialtyEmergencyConditionsHandled();
        return handled != null && handled.stream().anyMatch(condition -> condition.equalsIgnoreCase(emergencyType));
    }

    private EmergencyHospitalSuggestion toSuggestion(Hospital hospital) {
        List<Doctor> availableDoctors = doctorRepository.findByCurrentHospitalIdAndActiveTrue(hospital.getId());

        List<String> availableSpecialties = availableDoctors.stream()
                .filter(doctor -> doctor.getSpecialties() != null)
                .flatMap(doctor -> doctor.getSpecialties().stream())
                .distinct()
                .toList();

        return EmergencyHospitalSuggestion.builder()
                .hospitalId(hospital.getId())
                .hospitalName(hospital.getHospitalName())
                .address(hospital.getAddress())
                .contactDetails(hospital.getContactDetails())
                .availableDoctorCount(availableDoctors.size())
                .availableSpecialties(availableSpecialties)
                .build();
    }
}
