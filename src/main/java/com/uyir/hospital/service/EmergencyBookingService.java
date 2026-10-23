package com.uyir.hospital.service;

import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import java.util.List;

public interface EmergencyBookingService {

    EmergencyBookingResponse create(String patientId, EmergencyBookingRequest request);

    // Hospital: REQUESTED -> ACCEPTED / REJECTED, then ACCEPTED -> COMPLETED. Own bookings only.
    EmergencyBookingResponse accept(String id, String hospitalId);

    EmergencyBookingResponse reject(String id, String hospitalId, String reason);

    EmergencyBookingResponse complete(String id, String hospitalId);

    // Patient: REQUESTED / ACCEPTED -> CANCELLED. Own bookings only.
    EmergencyBookingResponse cancel(String id, String patientId, String reason);

    List<EmergencyBookingResponse> getByHospitalId(String hospitalId);

    List<EmergencyBookingResponse> getByPatientId(String patientId);
}
