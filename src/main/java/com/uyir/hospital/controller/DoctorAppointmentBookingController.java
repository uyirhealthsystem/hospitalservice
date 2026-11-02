package com.uyir.hospital.controller;

import com.uyir.hospital.dto.AppointmentDetailResponse;
import com.uyir.hospital.dto.CancelAppointmentRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.FollowUpRequest;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.dto.TokenAvailabilityResponse;
import com.uyir.hospital.dto.UpdateAppointmentStatusRequest;
import com.uyir.hospital.security.CurrentUserContext;
import com.uyir.hospital.security.Role;
import com.uyir.hospital.service.DoctorAppointmentBookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/hospital/doctor-appointments")
@RequiredArgsConstructor
@Tag(name = "Doctor Appointment Bookings")
public class DoctorAppointmentBookingController {

    private final DoctorAppointmentBookingService doctorAppointmentBookingService;
    private final CurrentUserContext currentUserContext;

    // Patient-only: books an appointment with a doctor at a hospital.
    @PostMapping
    public ResponseEntity<DoctorAppointmentBookingResponse> create(
            @Valid @RequestBody DoctorAppointmentBookingRequest request) {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        DoctorAppointmentBookingResponse created = doctorAppointmentBookingService.create(patientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Hospital-only: the logged-in hospital's own appointment bookings.
    @GetMapping
    public List<DoctorAppointmentBookingResponse> getMyBookings() {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorAppointmentBookingService.getByHospitalId(hospitalId);
    }

    // Any caller: how many appointment tokens the hospital issued for this doctor on the date and how
    // many are left, so the app can show "12 of 30 left" or "full" before the patient books.
    @GetMapping("/availability")
    public TokenAvailabilityResponse getAvailability(
            @RequestParam String hospitalId,
            @RequestParam String doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return doctorAppointmentBookingService.getAvailability(hospitalId, doctorId, date);
    }

    // Patient-only: the logged-in patient's own appointment booking history.
    @GetMapping("/history")
    public List<DoctorAppointmentBookingResponse> getMyHistory() {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        return doctorAppointmentBookingService.getByPatientId(patientId);
    }

    // Patient-only: one of the logged-in patient's own appointments, with the hospital and doctor details.
    @GetMapping("/{id}")
    public AppointmentDetailResponse getMyAppointment(@PathVariable String id) {
        String patientId = currentUserContext.requireRole(Role.PATIENT);
        return doctorAppointmentBookingService.getDetailForPatient(id, patientId);
    }

    // Hospital-only: appointment bookings can be rescheduled; emergency bookings cannot.
    @PatchMapping("/{id}/reschedule")
    public DoctorAppointmentBookingResponse reschedule(
            @PathVariable String id, @Valid @RequestBody RescheduleAppointmentRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorAppointmentBookingService.reschedule(id, hospitalId, request);
    }

    // Patient or hospital: either side can cancel its own appointment, with a reason.
    @PatchMapping("/{id}/cancel")
    public DoctorAppointmentBookingResponse cancel(
            @PathVariable String id, @Valid @RequestBody CancelAppointmentRequest request) {
        String userId = currentUserContext.requireAnyRole(Role.PATIENT, Role.HOSPITAL);
        Role role = currentUserContext.getRole();
        if (role == Role.HOSPITAL) {
            userId = currentUserContext.requireHospitalId();
        }
        return doctorAppointmentBookingService.cancel(id, userId, role, request);
    }

    // Hospital-only: closes out an appointment as COMPLETED or NO_SHOW once its time has come.
    @PatchMapping("/{id}/status")
    public DoctorAppointmentBookingResponse updateStatus(
            @PathVariable String id, @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorAppointmentBookingService.updateStatus(id, hospitalId, request);
    }

    // Hospital-only: ask the patient of a COMPLETED appointment to come back for a follow-up, or change
    // that instruction. Locked once the patient has booked the follow-up (409).
    @PutMapping("/{id}/follow-up")
    public DoctorAppointmentBookingResponse setFollowUp(
            @PathVariable String id, @Valid @RequestBody FollowUpRequest request) {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorAppointmentBookingService.setFollowUp(id, hospitalId, request);
    }

    // Hospital-only: the follow-up is no longer needed.
    @DeleteMapping("/{id}/follow-up")
    public DoctorAppointmentBookingResponse removeFollowUp(@PathVariable String id) {
        String hospitalId = currentUserContext.requireHospitalId();
        return doctorAppointmentBookingService.removeFollowUp(id, hospitalId);
    }
}
