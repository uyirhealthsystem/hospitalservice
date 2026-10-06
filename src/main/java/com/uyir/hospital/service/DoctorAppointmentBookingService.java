package com.uyir.hospital.service;

import com.uyir.hospital.dto.CancelAppointmentRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.dto.UpdateAppointmentStatusRequest;
import com.uyir.hospital.security.Role;
import java.util.List;

public interface DoctorAppointmentBookingService {

    DoctorAppointmentBookingResponse create(String patientId, DoctorAppointmentBookingRequest request);

    List<DoctorAppointmentBookingResponse> getByHospitalId(String hospitalId);

    List<DoctorAppointmentBookingResponse> getByPatientId(String patientId);

    DoctorAppointmentBookingResponse reschedule(String id, String hospitalId, RescheduleAppointmentRequest request);

    DoctorAppointmentBookingResponse cancel(String id, String userId, Role role, CancelAppointmentRequest request);

    DoctorAppointmentBookingResponse updateStatus(String id, String hospitalId, UpdateAppointmentStatusRequest request);
}
