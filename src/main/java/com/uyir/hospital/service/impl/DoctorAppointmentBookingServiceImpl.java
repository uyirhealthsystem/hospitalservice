package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.DoctorAppointmentBookingRequest;
import com.uyir.hospital.dto.DoctorAppointmentBookingResponse;
import com.uyir.hospital.dto.RescheduleAppointmentRequest;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.mapper.DoctorAppointmentBookingMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.DoctorAppointmentBookingService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorAppointmentBookingServiceImpl implements DoctorAppointmentBookingService {

    private final DoctorAppointmentBookingRepository doctorAppointmentBookingRepository;
    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorAppointmentBookingMapper doctorAppointmentBookingMapper;

    @Override
    public DoctorAppointmentBookingResponse create(String patientId, DoctorAppointmentBookingRequest request) {
        if (!hospitalRepository.existsById(request.getHospitalId())) {
            throw new ResourceNotFoundException("Hospital not found with id '" + request.getHospitalId() + "'");
        }

        Doctor doctor = doctorRepository
                .findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id '" + request.getDoctorId() + "'"));

        if (!doctor.isActive()) {
            throw new IllegalArgumentException("Cannot book an appointment with an inactive doctor");
        }

        List<HospitalAssociation> associations = doctor.getHospitalAssociations();
        boolean associated = associations != null
                && associations.stream().anyMatch(assoc -> request.getHospitalId().equals(assoc.getHospitalId()));
        if (!associated) {
            throw new IllegalArgumentException(
                    "Doctor '" + request.getDoctorId() + "' is not associated with hospital '" + request.getHospitalId() + "'");
        }

        Instant now = Instant.now();
        DoctorAppointmentBooking booking = DoctorAppointmentBooking.builder()
                .patientId(patientId)
                .patientName(request.getPatientName())
                .patientPhone(request.getPatientPhone())
                .hospitalId(request.getHospitalId())
                .doctorId(request.getDoctorId())
                .appointmentDateTime(request.getAppointmentDateTime())
                .reason(request.getReason())
                .status(AppointmentStatus.CONFIRMED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return doctorAppointmentBookingMapper.toResponse(doctorAppointmentBookingRepository.save(booking));
    }

    @Override
    public List<DoctorAppointmentBookingResponse> getByHospitalId(String hospitalId) {
        return doctorAppointmentBookingRepository.findByHospitalIdOrderByAppointmentDateTimeAsc(hospitalId).stream()
                .map(doctorAppointmentBookingMapper::toResponse)
                .toList();
    }

    @Override
    public List<DoctorAppointmentBookingResponse> getByPatientId(String patientId) {
        return doctorAppointmentBookingRepository.findByPatientIdOrderByAppointmentDateTimeDesc(patientId).stream()
                .map(doctorAppointmentBookingMapper::toResponse)
                .toList();
    }

    @Override
    public DoctorAppointmentBookingResponse reschedule(String id, String hospitalId, RescheduleAppointmentRequest request) {
        DoctorAppointmentBooking booking = doctorAppointmentBookingRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment booking not found with id '" + id + "'"));

        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new ForbiddenException("Appointment booking '" + id + "' does not belong to hospital '" + hospitalId + "'");
        }

        if (booking.getStatus() == AppointmentStatus.CANCELLED || booking.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot reschedule an appointment with status " + booking.getStatus());
        }

        booking.setAppointmentDateTime(request.getAppointmentDateTime());
        booking.setStatus(AppointmentStatus.RESCHEDULED);
        booking.setUpdatedAt(Instant.now());

        return doctorAppointmentBookingMapper.toResponse(doctorAppointmentBookingRepository.save(booking));
    }
}
