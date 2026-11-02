package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.PageResponse;
import com.uyir.hospital.dto.PatientActivityItem;
import com.uyir.hospital.dto.PatientActivityResponse;
import com.uyir.hospital.dto.PatientActivitySummary;
import com.uyir.hospital.mapper.DoctorAppointmentBookingMapper;
import com.uyir.hospital.mapper.EmergencyBookingMapper;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.model.enums.PatientActivityType;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.PatientActivityService;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatientActivityServiceImpl implements PatientActivityService {

    private static final Set<EmergencyBookingStatus> ACTIVE_EMERGENCY =
            EnumSet.of(EmergencyBookingStatus.REQUESTED, EmergencyBookingStatus.ACCEPTED);
    private static final Set<AppointmentStatus> OPEN_APPOINTMENT =
            EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED);

    private final EmergencyBookingRepository emergencyBookingRepository;
    private final DoctorAppointmentBookingRepository doctorAppointmentBookingRepository;
    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final EmergencyBookingMapper emergencyBookingMapper;
    private final DoctorAppointmentBookingMapper doctorAppointmentBookingMapper;

    @Override
    public PatientActivityResponse getActivity(String patientId, PatientActivityType type, Pageable pageable) {
        Instant now = Instant.now();
        PatientActivitySummary summary = summarize(patientId, now);

        boolean includeEmergencies = type != PatientActivityType.APPOINTMENT;
        boolean includeAppointments = type != PatientActivityType.EMERGENCY;
        long total = (includeEmergencies ? summary.getTotalEmergencies() : 0)
                + (includeAppointments ? summary.getTotalAppointments() : 0);

        // The timeline merges two collections, so page p of size s can't be read from either one alone. Its rows
        // are always among the newest (p + 1) * s of each, so fetch that many from both, merge, and cut the page.
        long offset = (long) pageable.getPageNumber() * pageable.getPageSize();
        List<EmergencyBooking> emergencies = List.of();
        List<DoctorAppointmentBooking> appointments = List.of();
        if (offset < total) {
            int window = (int) (offset + pageable.getPageSize());
            if (includeEmergencies && summary.getTotalEmergencies() > 0) {
                emergencies = emergencyBookingRepository.findByPatientId(
                        patientId, PageRequest.of(0, window, Sort.by(Sort.Direction.DESC, "requestedAt")));
            }
            if (includeAppointments && summary.getTotalAppointments() > 0) {
                appointments = doctorAppointmentBookingRepository.findByPatientId(
                        patientId, PageRequest.of(0, window, Sort.by(Sort.Direction.DESC, "appointmentDateTime")));
            }
        }

        List<PatientActivityItem> page = Stream.concat(
                        emergencies.stream().map(this::toItem),
                        appointments.stream().map(a -> toItem(a, now)))
                .sorted(Comparator.comparing(PatientActivityItem::getActivityAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .skip(offset)
                .limit(pageable.getPageSize())
                .toList();
        fillNames(page);

        return PatientActivityResponse.builder()
                .patientId(patientId)
                .summary(summary)
                .activities(PageResponse.from(
                        new PageImpl<>(page, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), total)))
                .build();
    }

    // Counted in the database rather than from the fetched window, so it covers the patient's whole history.
    private PatientActivitySummary summarize(String patientId, Instant now) {
        return PatientActivitySummary.builder()
                .totalEmergencies(emergencyBookingRepository.countByPatientId(patientId))
                .activeEmergencies(emergencyBookingRepository.countByPatientIdAndStatusIn(patientId, ACTIVE_EMERGENCY))
                .completedEmergencies(emergencyBookingRepository.countByPatientIdAndStatusIn(
                        patientId, EnumSet.of(EmergencyBookingStatus.COMPLETED)))
                .totalAppointments(doctorAppointmentBookingRepository.countByPatientId(patientId))
                .upcomingAppointments(doctorAppointmentBookingRepository
                        .countByPatientIdAndStatusInAndAppointmentDateTimeAfter(patientId, OPEN_APPOINTMENT, now))
                .completedAppointments(countAppointments(patientId, AppointmentStatus.COMPLETED))
                .cancelledAppointments(countAppointments(patientId, AppointmentStatus.CANCELLED))
                .missedAppointments(countAppointments(patientId, AppointmentStatus.NO_SHOW))
                .build();
    }

    private long countAppointments(String patientId, AppointmentStatus status) {
        return doctorAppointmentBookingRepository.countByPatientIdAndStatusIn(patientId, EnumSet.of(status));
    }

    // Display names for just the rows on the page - one lookup per collection rather than one per booking.
    private void fillNames(List<PatientActivityItem> page) {
        Set<String> hospitalIds = page.stream()
                .map(PatientActivityItem::getHospitalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> doctorIds = page.stream()
                .map(PatientActivityItem::getDoctorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> hospitalNames = hospitalIds.isEmpty() ? Map.of() : hospitalRepository
                .findAllById(hospitalIds).stream()
                .filter(h -> h.getHospitalName() != null)
                .collect(Collectors.toMap(Hospital::getId, Hospital::getHospitalName, (a, b) -> a));
        Map<String, String> doctorNames = doctorIds.isEmpty() ? Map.of() : doctorRepository
                .findAllById(doctorIds).stream()
                .filter(d -> d.getName() != null)
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName, (a, b) -> a));
        for (PatientActivityItem item : page) {
            item.setHospitalName(hospitalNames.get(item.getHospitalId()));
            if (item.getDoctorId() != null) {
                item.setDoctorName(doctorNames.get(item.getDoctorId()));
            }
        }
    }

    private PatientActivityItem toItem(EmergencyBooking booking) {
        return PatientActivityItem.builder()
                .type(PatientActivityType.EMERGENCY)
                .id(booking.getId())
                .hospitalId(booking.getHospitalId())
                .title(booking.getEmergencyType())
                .status(nameOf(booking.getStatus()))
                .patientName(booking.getPatientName())
                .activityAt(booking.getRequestedAt())
                .active(ACTIVE_EMERGENCY.contains(booking.getStatus()))
                .emergencyBooking(emergencyBookingMapper.toResponse(booking))
                .build();
    }

    private PatientActivityItem toItem(DoctorAppointmentBooking booking, Instant now) {
        return PatientActivityItem.builder()
                .type(PatientActivityType.APPOINTMENT)
                .id(booking.getId())
                .hospitalId(booking.getHospitalId())
                .doctorId(booking.getDoctorId())
                .title(booking.getReason())
                .status(nameOf(booking.getStatus()))
                .patientName(booking.getPatientName())
                .activityAt(booking.getAppointmentDateTime())
                .active(isUpcoming(booking, now))
                .appointment(doctorAppointmentBookingMapper.toResponse(booking))
                .build();
    }

    private static boolean isUpcoming(DoctorAppointmentBooking booking, Instant now) {
        return OPEN_APPOINTMENT.contains(booking.getStatus())
                && booking.getAppointmentDateTime() != null
                && booking.getAppointmentDateTime().isAfter(now);
    }

    private static String nameOf(Enum<?> status) {
        return status != null ? status.name() : null;
    }
}
