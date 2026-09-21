package com.uyir.hospital.service;

import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import java.util.List;

public interface DoctorAppointmentBookingService {

    DoctorAppointmentBookingResponse create(String patientId, DoctorAppointmentBookingRequest request);

    List<DoctorAppointmentBookingResponse> getByHospitalId(String hospitalId);

    List<DoctorAppointmentBookingResponse> getByPatientId(String patientId);

    DoctorAppointmentBookingResponse reschedule(String id, String hospitalId, RescheduleAppointmentRequest request);
}
