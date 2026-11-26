package com.uyir.hospital.service;

import com.uyir.hospital.dto.EmergencyBookingRequest;
import com.uyir.hospital.dto.EmergencyBookingResponse;
import com.uyir.hospital.dto.IncomingAmbulanceResponse;
import com.uyir.hospital.dto.IncomingPatientResponse;
import com.uyir.hospital.dto.PatientLocationRequest;
import com.uyir.hospital.model.enums.ArrivalMode;
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

    // Patient: how they are getting to the hospital (REQUESTED / ACCEPTED only). Own bookings only.
    EmergencyBookingResponse setArrivalMode(String id, String patientId, ArrivalMode mode);

    // Patient's phone: its position while on the way (ACCEPTED only). Own bookings only. Sent every few
    // seconds, so it raises no live event.
    void updatePatientLocation(String id, String patientId, PatientLocationRequest request);

    // Hospital: the patient phone's latest position and straight-line distance. Own ACCEPTED / COMPLETED only.
    IncomingPatientResponse incomingPatient(String id, String hospitalId);

    List<EmergencyBookingResponse> getByHospitalId(String hospitalId);

    List<EmergencyBookingResponse> getByPatientId(String patientId);
}
