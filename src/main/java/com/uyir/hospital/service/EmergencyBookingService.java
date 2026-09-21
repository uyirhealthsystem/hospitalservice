package com.uyir.hospital.service;

import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import java.util.List;

public interface EmergencyBookingService {

    EmergencyBookingResponse create(String patientId, EmergencyBookingRequest request);

    List<EmergencyBookingResponse> getByHospitalId(String hospitalId);

    List<EmergencyBookingResponse> getByPatientId(String patientId);
}
