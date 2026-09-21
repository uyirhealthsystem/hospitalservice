package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.EmergencyBookingMapper;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.EmergencyBookingService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmergencyBookingServiceImpl implements EmergencyBookingService {

    private final EmergencyBookingRepository emergencyBookingRepository;
    private final HospitalRepository hospitalRepository;
    private final EmergencyBookingMapper emergencyBookingMapper;

    @Override
    public EmergencyBookingResponse create(String patientId, EmergencyBookingRequest request) {
        Hospital hospital = hospitalRepository
                .findById(request.getHospitalId())
                .filter(Hospital::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id '" + request.getHospitalId() + "'"));

        // Re-validate capability server-side: the client picks a hospitalId from the emergency-sos
        // suggestions, but nothing stops a direct call with an id that was never suggested.
        if (hospital.getEmergencyServices() == null
                || !hospital.getEmergencyServices().handlesEmergencyType(request.getEmergencyType())) {
            throw new IllegalArgumentException(
                    "Hospital '" + request.getHospitalId() + "' does not handle emergency type '"
                            + request.getEmergencyType() + "'");
        }

        Instant now = Instant.now();
        EmergencyBooking booking = EmergencyBooking.builder()
                .patientId(patientId)
                .patientName(request.getPatientName())
                .patientPhone(request.getPatientPhone())
                .hospitalId(request.getHospitalId())
                .emergencyType(request.getEmergencyType())
                .status(EmergencyBookingStatus.REQUESTED)
                .requestedAt(now)
                .updatedAt(now)
                .build();

        return emergencyBookingMapper.toResponse(emergencyBookingRepository.save(booking));
    }

    @Override
    public List<EmergencyBookingResponse> getByHospitalId(String hospitalId) {
        return emergencyBookingRepository.findByHospitalIdOrderByRequestedAtDesc(hospitalId).stream()
                .map(emergencyBookingMapper::toResponse)
                .toList();
    }

    @Override
    public List<EmergencyBookingResponse> getByPatientId(String patientId) {
        return emergencyBookingRepository.findByPatientIdOrderByRequestedAtDesc(patientId).stream()
                .map(emergencyBookingMapper::toResponse)
                .toList();
    }
}
