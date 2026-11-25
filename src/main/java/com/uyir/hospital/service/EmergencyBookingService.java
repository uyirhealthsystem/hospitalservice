package com.uyir.hospital.service;

import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.dto.IncomingAmbulanceResponse;
import java.util.List;

public interface EmergencyBookingService {

    EmergencyBookingResponse create(String patientId, EmergencyBookingRequest request);

    // Hospital: REQUESTED -> ACCEPTED / REJECTED, then ACCEPTED -> COMPLETED. Own bookings only.
    EmergencyBookingResponse accept(String id, String hospitalId);

    EmergencyBookingResponse reject(String id, String hospitalId, String reason);

    EmergencyBookingResponse complete(String id, String hospitalId);

    // Patient: REQUESTED / ACCEPTED -> CANCELLED. Own bookings only.
    EmergencyBookingResponse cancel(String id, String patientId, String reason);

    // Patient: records the ambulance-service ride taking them to this hospital (REQUESTED / ACCEPTED only).
    // The ride must be the same patient's; linking again replaces it (e.g. a new ride after one was cancelled).
    EmergencyBookingResponse linkAmbulance(String id, String patientId, String ambulanceBookingId);

    // Hospital: the linked ambulance's live position and ETA. Own ACCEPTED / COMPLETED bookings only.
    IncomingAmbulanceResponse incomingAmbulance(String id, String hospitalId);

    List<EmergencyBookingResponse> getByHospitalId(String hospitalId);

    List<EmergencyBookingResponse> getByPatientId(String patientId);
}
