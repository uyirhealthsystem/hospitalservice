package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.HospitalMetricsResponse;
import com.uyir.hospital.dto.HospitalMetricsResponse.AppointmentMetrics;
import com.uyir.hospital.dto.HospitalMetricsResponse.DoctorMetrics;
import com.uyir.hospital.dto.HospitalMetricsResponse.EmergencyBookingMetrics;
import com.uyir.hospital.dto.HospitalMetricsResponse.HospitalMetrics;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.HospitalMetricsService;
import com.uyir.hospital.service.impl.HospitalAnalyticsServiceImpl.DateRange;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Counts done as Mongo count queries - unlike the district analytics, bookings and doctors are
// never loaded into memory, so this stays cheap as the collections grow.
@Service
@RequiredArgsConstructor
public class HospitalMetricsServiceImpl implements HospitalMetricsService {

    // Emergencies still in progress: waiting for the hospital, or accepted but not yet completed.
    private static final Set<EmergencyBookingStatus> OPEN_EMERGENCY_STATUSES =
            EnumSet.of(EmergencyBookingStatus.REQUESTED, EmergencyBookingStatus.ACCEPTED);
    private static final Set<AppointmentStatus> UPCOMING_STATUSES =
            EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED);

    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final EmergencyBookingRepository emergencyBookingRepository;
    private final DoctorAppointmentBookingRepository appointmentBookingRepository;

    @Override
    public HospitalMetricsResponse getMetrics(LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        Instant now = Instant.now();

        long totalHospitals = hospitalRepository.count();
        long activeHospitals = hospitalRepository.countByActiveTrue();

        Map<EmergencyBookingStatus, Long> emergencyByStatus = countPerStatus(EmergencyBookingStatus.class,
                status -> emergencyBookingRepository.countByStatusRequestedBetween(status, range.start(), range.end()));
        Map<AppointmentStatus, Long> appointmentsByStatus = countPerStatus(AppointmentStatus.class,
                status -> appointmentBookingRepository.countByStatusScheduledBetween(status, range.start(), range.end()));

        return HospitalMetricsResponse.builder()
                .fromDate(range.from())
                .toDate(range.to())
                .generatedAt(now)
                .hospitals(HospitalMetrics.builder()
                        .total(totalHospitals)
                        .active(activeHospitals)
                        .inactive(totalHospitals - activeHospitals)
                        .handlingEmergencies(hospitalRepository.countByActiveTrueAndEmergencyServicesHandlesEmergenciesTrue())
                        .build())
                .doctors(DoctorMetrics.builder()
                        .total(doctorRepository.count())
                        .active(doctorRepository.countByActiveTrue())
                        .checkedInNow(doctorRepository.countByActiveTrueAndCurrentHospitalIdIsNotNull())
                        .build())
                .emergencyBookings(EmergencyBookingMetrics.builder()
                        .total(sum(emergencyByStatus))
                        .byStatus(emergencyByStatus)
                        .openNow(emergencyBookingRepository.countByStatusIn(OPEN_EMERGENCY_STATUSES))
                        .build())
                .appointments(AppointmentMetrics.builder()
                        .total(sum(appointmentsByStatus))
                        .byStatus(appointmentsByStatus)
                        .upcoming(appointmentBookingRepository.countByStatusInScheduledFrom(UPCOMING_STATUSES, now))
                        .build())
                .build();
    }

    // Same district rule as the analytics: Hospital.address.district, case-insensitive. A district
    // has few hospitals, so those are loaded; doctors and bookings are counted by hospital id.
    @Override
    public HospitalMetricsResponse getDistrictMetrics(String district, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        HospitalMetricsResponse response = metricsFor(hospitalRepository.findByAddressDistrictIgnoreCase(district), range);
        response.setDistrict(district);
        return response;
    }

    // The same counts for a single hospital - what a HOSPITAL caller sees about itself.
    @Override
    public HospitalMetricsResponse getHospitalMetrics(String hospitalId, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id '" + hospitalId + "'"));
        HospitalMetricsResponse response = metricsFor(List.of(hospital), range);
        response.setHospitalId(hospitalId);
        return response;
    }

    private HospitalMetricsResponse metricsFor(List<Hospital> hospitals, DateRange range) {
        Instant now = Instant.now();

        Set<String> ids = hospitals.stream().map(Hospital::getId).collect(Collectors.toSet());
        List<Hospital> activeHospitals = hospitals.stream().filter(Hospital::isActive).toList();
        // An empty $in matches nothing anyway - skip the round trips.
        boolean none = ids.isEmpty();

        Map<EmergencyBookingStatus, Long> emergencyByStatus = countPerStatus(EmergencyBookingStatus.class,
                status -> none ? 0 : emergencyBookingRepository.countByHospitalIdsStatusRequestedBetween(
                        ids, status, range.start(), range.end()));
        Map<AppointmentStatus, Long> appointmentsByStatus = countPerStatus(AppointmentStatus.class,
                status -> none ? 0 : appointmentBookingRepository.countByHospitalIdsStatusScheduledBetween(
                        ids, status, range.start(), range.end()));

        return HospitalMetricsResponse.builder()
                .fromDate(range.from())
                .toDate(range.to())
                .generatedAt(now)
                .hospitals(HospitalMetrics.builder()
                        .total(hospitals.size())
                        .active(activeHospitals.size())
                        .inactive(hospitals.size() - activeHospitals.size())
                        .handlingEmergencies(activeHospitals.stream()
                                .filter(h -> h.getEmergencyServices() != null
                                        && h.getEmergencyServices().isHandlesEmergencies())
                                .count())
                        .build())
                .doctors(DoctorMetrics.builder()
                        .total(none ? 0 : doctorRepository.countByHospitalAssociationsHospitalIdIn(ids))
                        .active(none ? 0 : doctorRepository.countByHospitalAssociationsHospitalIdInAndActiveTrue(ids))
                        .checkedInNow(none ? 0 : doctorRepository.countByCurrentHospitalIdInAndActiveTrue(ids))
                        .build())
                .emergencyBookings(EmergencyBookingMetrics.builder()
                        .total(sum(emergencyByStatus))
                        .byStatus(emergencyByStatus)
                        .openNow(none ? 0 : emergencyBookingRepository.countByHospitalIdInAndStatusIn(
                                ids, OPEN_EMERGENCY_STATUSES))
                        .build())
                .appointments(AppointmentMetrics.builder()
                        .total(sum(appointmentsByStatus))
                        .byStatus(appointmentsByStatus)
                        .upcoming(none ? 0 : appointmentBookingRepository.countByHospitalIdsStatusInScheduledFrom(
                                ids, UPCOMING_STATUSES, now))
                        .build())
                .build();
    }

    // Every enum constant is present (zero when absent) so the response shape is stable.
    private static <E extends Enum<E>> Map<E, Long> countPerStatus(Class<E> enumType, ToLongFunction<E> counter) {
        Map<E, Long> counts = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            counts.put(value, counter.applyAsLong(value));
        }
        return counts;
    }

    private static long sum(Map<?, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}
