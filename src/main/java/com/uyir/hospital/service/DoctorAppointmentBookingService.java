package com.uyir.hospital.service;

import com.uyir.hospital.dto.AppointmentDetailResponse;
import com.uyir.hospital.dto.CancelAppointmentRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.dto.UpdateAppointmentStatusRequest;
import com.uyir.hospital.dto.FollowUpRequest;
import com.uyir.hospital.dto.TokenAvailabilityResponse;
import com.uyir.hospital.security.Role;
import java.time.LocalDate;
import java.util.List;

public interface DoctorAppointmentBookingService {

    DoctorAppointmentBookingResponse create(String patientId, DoctorAppointmentBookingRequest request);

    // Token limit, booked and remaining tokens for a doctor at a hospital on one date.
    TokenAvailabilityResponse getAvailability(String hospitalId, String doctorId, LocalDate date);

    List<DoctorAppointmentBookingResponse> getByHospitalId(String hospitalId);

    List<DoctorAppointmentBookingResponse> getByPatientId(String patientId);

    // Patient: one of their own appointments, with the hospital and doctor details.
    AppointmentDetailResponse getDetailForPatient(String id, String patientId);

    DoctorAppointmentBookingResponse reschedule(String id, String hospitalId, RescheduleAppointmentRequest request);

    DoctorAppointmentBookingResponse cancel(String id, String userId, Role role, CancelAppointmentRequest request);

    DoctorAppointmentBookingResponse updateStatus(String id, String hospitalId, UpdateAppointmentStatusRequest request);

    // Hospital: ask the patient of a COMPLETED appointment to come back (sets or replaces the instruction).
    DoctorAppointmentBookingResponse setFollowUp(String id, String hospitalId, FollowUpRequest request);

    DoctorAppointmentBookingResponse removeFollowUp(String id, String hospitalId);
}
