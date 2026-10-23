package com.uyir.hospital.service;

import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.repository.DoctorRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Decides whether a hospital can take an emergency right now. Shared by emergency SOS (which hospitals to
// suggest) and emergency booking (re-checked server-side), so the two can never disagree. All four must hold:
//   1. the hospital is active and handles emergencies (emergencyServices.handlesEmergencies)
//   2. the hospital chose the condition (emergencyServices.specialtyEmergencyConditionsHandled)
//   3. a doctor associated with it chose the condition too (Doctor.emergencyConditionsHandled)
//   4. that doctor is on duty there now: active, checked in at this hospital (check-in already requires a
//      hospitalAssociations entry for it) and holding one of its seats
// `condition` is the official name (EmergencyConditionCatalog.requireName).
@Component
@RequiredArgsConstructor
public class EmergencyCoverage {

    private final DoctorRepository doctorRepository;
    private final HospitalSeatService hospitalSeatService;

    // Rules 1 and 2 - the hospital's own data, no doctor lookup.
    public boolean hospitalHandles(Hospital hospital, String condition) {
        EmergencyServices emergencyServices = hospital.getEmergencyServices();
        return hospital.isActive() && emergencyServices != null && emergencyServices.handlesEmergencyType(condition);
    }

    // Rules 3 and 4 - the doctors on duty at this hospital right now who handle the condition. Empty means the
    // hospital can't take it, whatever rules 1 and 2 say.
    public List<Doctor> onDutyDoctorsHandling(Hospital hospital, String condition) {
        Set<String> seatedDoctorIds = hospitalSeatService.seatedDoctorIds(hospital.getId());
        return doctorRepository.findByCurrentHospitalIdAndActiveTrue(hospital.getId()).stream()
                .filter(doctor -> seatedDoctorIds.contains(doctor.getId()))
                .filter(doctor -> handles(doctor, condition))
                .toList();
    }

    private static boolean handles(Doctor doctor, String condition) {
        return doctor.getEmergencyConditionsHandled() != null
                && doctor.getEmergencyConditionsHandled().stream().anyMatch(condition::equalsIgnoreCase);
    }
}
