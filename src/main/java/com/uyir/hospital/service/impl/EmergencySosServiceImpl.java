package com.uyir.hospital.service.impl;

import com.uyir.hospital.catalog.EmergencyConditionCatalog;
import com.uyir.hospital.dto.EmergencyHospitalSuggestion;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.EmergencyCoverage;
import com.uyir.hospital.service.EmergencySosService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmergencySosServiceImpl implements EmergencySosService {

    private final HospitalRepository hospitalRepository;
    private final EmergencyConditionCatalog emergencyConditionCatalog;
    private final EmergencyCoverage emergencyCoverage;

    @Override
    public List<EmergencyHospitalSuggestion> findAvailableHospitals(
            String emergencyType, double longitude, double latitude, double radiusKm) {

        GeoQuerySupport.validate(longitude, latitude, radiusKm);
        // One of the 16 SOS emergency conditions (code or name) -> its official name; 400 otherwise.
        String condition = emergencyConditionCatalog.requireName(emergencyType);

        Point point = new Point(longitude, latitude);
        Distance distance = new Distance(radiusKm, Metrics.KILOMETERS);

        // findByAddressLocationNear compiles to a MongoDB $near query, which returns
        // results nearest-first - no separate distance sort/computation needed here.
        return hospitalRepository.findByAddressLocationNear(point, distance).stream()
                .filter(hospital -> emergencyCoverage.hospitalHandles(hospital, condition))
                .map(hospital -> toSuggestion(hospital, emergencyCoverage.onDutyDoctorsHandling(hospital, condition)))
                .filter(Objects::nonNull)
                .toList();
    }

    // null when no doctor on duty there handles the condition - the hospital is left out entirely.
    private EmergencyHospitalSuggestion toSuggestion(Hospital hospital, List<Doctor> doctors) {
        if (doctors.isEmpty()) {
            return null;
        }
        List<String> availableSpecialties = doctors.stream()
                .filter(doctor -> doctor.getSpecialties() != null)
                .flatMap(doctor -> doctor.getSpecialties().stream())
                .distinct()
                .toList();

        return EmergencyHospitalSuggestion.builder()
                .hospitalId(hospital.getId())
                .hospitalName(hospital.getHospitalName())
                .address(hospital.getAddress())
                .contactDetails(hospital.getContactDetails())
                .availableDoctorCount(doctors.size())
                .availableSpecialties(availableSpecialties)
                .build();
    }
}
