package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.HospitalMatricsResponse;
import com.uyir.hospital.dto.HospitalMatricsResponse.AppointmentMatrics;
import com.uyir.hospital.dto.HospitalMatricsResponse.DoctorMatrics;
import com.uyir.hospital.dto.HospitalMatricsResponse.EmergencyBookingMatrics;
import com.uyir.hospital.dto.HospitalMatricsResponse.HospitalMatrics;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.HospitalMatricsService;
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
public class HospitalMatricsServiceImpl implements HospitalMatricsService {

    private static final Set<AppointmentStatus> UPCOMING_STATUSES =
            EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULED);

    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final EmergencyBookingRepository emergencyBookingRepository;
    private final DoctorAppointmentBookingRepository appointmentBookingRepository;

    @Override
    public HospitalMatricsResponse getMatrics(LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        Instant now = Instant.now();

        long totalHospitals = hospitalRepository.count();
        long activeHospitals = hospitalRepository.countByActiveTrue();

        Map<EmergencyBookingStatus, Long> emergencyByStatus = countPerStatus(EmergencyBookingStatus.class,
                status -> emergencyBookingRepository.countByStatusRequestedBetween(status, range.start(), range.end()));
        Map<AppointmentStatus, Long> appointmentsByStatus = countPerStatus(AppointmentStatus.class,
                status -> appointmentBookingRepository.countByStatusScheduledBetween(status, range.start(), range.end()));

        return HospitalMatricsResponse.builder()
                .fromDate(range.from())
                .toDate(range.to())
                .generatedAt(now)
                .hospitals(HospitalMatrics.builder()
                        .total(totalHospitals)
                        .active(activeHospitals)
                        .inactive(totalHospitals - activeHospitals)
                        .handlingEmergencies(hospitalRepository.countByActiveTrueAndEmergencyServicesHandlesEmergenciesTrue())
                        .build())
                .doctors(DoctorMatrics.builder()
                        .total(doctorRepository.count())
                        .active(doctorRepository.countByActiveTrue())
                        .checkedInNow(doctorRepository.countByActiveTrueAndCurrentHospitalIdIsNotNull())
                        .build())
                .emergencyBookings(EmergencyBookingMatrics.builder()
                        .total(sum(emergencyByStatus))
                        .byStatus(emergencyByStatus)
                        .openNow(emergencyBookingRepository.countByStatus(EmergencyBookingStatus.REQUESTED))
                        .build())
                .appointments(AppointmentMatrics.builder()
                        .total(sum(appointmentsByStatus))
                        .byStatus(appointmentsByStatus)
                        .upcoming(appointmentBookingRepository.countByStatusInScheduledFrom(UPCOMING_STATUSES, now))
                        .build())
                .build();
    }

    // Same district rule as the analytics: Hospital.address.district, case-insensitive. A district
    // has few hospitals, so those are loaded; doctors and bookings are counted by hospital id.
    @Override
    public HospitalMatricsResponse getDistrictMatrics(String district, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        Instant now = Instant.now();

        List<Hospital> hospitals = hospitalRepository.findByAddressDistrictIgnoreCase(district);
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

        return HospitalMatricsResponse.builder()
                .district(district)
                .fromDate(range.from())
                .toDate(range.to())
                .generatedAt(now)
                .hospitals(HospitalMatrics.builder()
                        .total(hospitals.size())
                        .active(activeHospitals.size())
                        .inactive(hospitals.size() - activeHospitals.size())
                        .handlingEmergencies(activeHospitals.stream()
                                .filter(h -> h.getEmergencyServices() != null
                                        && h.getEmergencyServices().isHandlesEmergencies())
                                .count())
                        .build())
                .doctors(DoctorMatrics.builder()
                        .total(none ? 0 : doctorRepository.countByHospitalAssociationsHospitalIdIn(ids))
                        .active(none ? 0 : doctorRepository.countByHospitalAssociationsHospitalIdInAndActiveTrue(ids))
                        .checkedInNow(none ? 0 : doctorRepository.countByCurrentHospitalIdInAndActiveTrue(ids))
                        .build())
                .emergencyBookings(EmergencyBookingMatrics.builder()
                        .total(sum(emergencyByStatus))
                        .byStatus(emergencyByStatus)
                        .openNow(none ? 0 : emergencyBookingRepository.countByHospitalIdInAndStatus(
                                ids, EmergencyBookingStatus.REQUESTED))
                        .build())
                .appointments(AppointmentMatrics.builder()
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
