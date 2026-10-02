package com.uyir.hospital.service.impl;

import com.uyir.hospital.dto.AnalyticsSnapshotRequest;
import com.uyir.hospital.dto.AnalyticsSnapshotResponse;
import com.uyir.hospital.dto.AnalyticsTrendPoint;
import com.uyir.hospital.dto.DistrictAnalyticsSummary;
import com.uyir.hospital.dto.DistrictAnalyticsSummary.AppointmentStats;
import com.uyir.hospital.dto.DistrictAnalyticsSummary.BedStats;
import com.uyir.hospital.dto.DistrictAnalyticsSummary.DoctorStats;
import com.uyir.hospital.dto.DistrictAnalyticsSummary.EmergencyBookingStats;
import com.uyir.hospital.dto.DistrictAnalyticsSummary.HospitalStats;
import com.uyir.hospital.dto.HospitalAnalyticsResponse;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.AnalyticsSnapshot;
import com.uyir.hospital.model.Doctor;
import com.uyir.hospital.model.DoctorAppointmentBooking;
import com.uyir.hospital.model.EmergencyBooking;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.model.embedded.BedCapacity;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.embedded.Facilities;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import com.uyir.hospital.repository.AnalyticsSnapshotRepository;
import com.uyir.hospital.repository.DoctorAppointmentBookingRepository;
import com.uyir.hospital.repository.DoctorRepository;
import com.uyir.hospital.repository.EmergencyBookingRepository;
import com.uyir.hospital.repository.HospitalRepository;
import com.uyir.hospital.service.HospitalAnalyticsService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Everything is scoped to the caller's district (Hospital.address.district, case-insensitive);
// bookings and doctors are pulled in only through the ids of hospitals in that district.
@Service
@RequiredArgsConstructor
public class HospitalAnalyticsServiceImpl implements HospitalAnalyticsService {

    // Day boundaries for date ranges and trend buckets follow local (IST) days, not UTC.
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final int DEFAULT_RANGE_DAYS = 30;
    static final int MAX_RANGE_DAYS = 366;

    private final HospitalRepository hospitalRepository;
    private final DoctorRepository doctorRepository;
    private final EmergencyBookingRepository emergencyBookingRepository;
    private final DoctorAppointmentBookingRepository appointmentBookingRepository;
    private final AnalyticsSnapshotRepository snapshotRepository;

    @Override
    public DistrictAnalyticsSummary getSummary(String district, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        DistrictData data = load(hospitalRepository.findByAddressDistrictIgnoreCase(district), range);
        List<Hospital> activeHospitals = data.hospitals.stream().filter(Hospital::isActive).toList();

        long general = sumBeds(activeHospitals, BedCapacity::getGeneral);
        long icu = sumBeds(activeHospitals, BedCapacity::getIcu);
        long nicu = sumBeds(activeHospitals, BedCapacity::getNicu);

        return DistrictAnalyticsSummary.builder()
                .district(district)
                .fromDate(range.from)
                .toDate(range.to)
                .generatedAt(Instant.now())
                .hospitals(HospitalStats.builder()
                        .total(data.hospitals.size())
                        .active(activeHospitals.size())
                        .inactive(data.hospitals.size() - activeHospitals.size())
                        .handlingEmergencies(countWithFlag(activeHospitals, EmergencyServices::isHandlesEmergencies))
                        .withAmbulance(countWithFlag(activeHospitals, EmergencyServices::isAmbulanceAvailable))
                        .withAmbulance24x7(countWithFlag(activeHospitals, EmergencyServices::isAmbulance24x7Available))
                        .byType(countByEnum(data.hospitals, Hospital::getHospitalType, HospitalType.class))
                        .byOwnership(countByEnum(data.hospitals, Hospital::getOwnershipType, OwnershipType.class))
                        .build())
                .beds(BedStats.builder().general(general).icu(icu).nicu(nicu).total(general + icu + nicu).build())
                .doctors(DoctorStats.builder()
                        .associated(data.doctors.size())
                        .checkedInNow(data.doctors.stream()
                                .filter(d -> data.hospitalIds.contains(d.getCurrentHospitalId()))
                                .count())
                        .build())
                .emergencyBookings(EmergencyBookingStats.builder()
                        .total(data.emergencyBookings.size())
                        .byStatus(countByEnum(data.emergencyBookings, EmergencyBooking::getStatus, EmergencyBookingStatus.class))
                        .byEmergencyType(countByEmergencyType(data.emergencyBookings))
                        .build())
                .appointments(AppointmentStats.builder()
                        .total(data.appointments.size())
                        .byStatus(countByEnum(data.appointments, DoctorAppointmentBooking::getStatus, AppointmentStatus.class))
                        .build())
                .build();
    }

    @Override
    public List<HospitalAnalyticsResponse> getHospitalBreakdown(String district, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        DistrictData data = load(hospitalRepository.findByAddressDistrictIgnoreCase(district), range);
        return data.hospitals.stream()
                .sorted(Comparator.comparing(Hospital::getHospitalName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(hospital -> toHospitalAnalytics(hospital, data))
                .toList();
    }

    @Override
    public HospitalAnalyticsResponse getHospitalAnalytics(
            String district, String hospitalId, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id '" + hospitalId + "'"));
        if (!inDistrict(hospital, district)) {
            throw new ForbiddenException("Hospital '" + hospitalId + "' is not in district '" + district + "'");
        }
        return toHospitalAnalytics(hospital, load(List.of(hospital), range));
    }

    @Override
    public List<AnalyticsTrendPoint> getTrends(String district, LocalDate fromDate, LocalDate toDate) {
        DateRange range = DateRange.resolve(fromDate, toDate);
        DistrictData data = load(hospitalRepository.findByAddressDistrictIgnoreCase(district), range);

        Map<LocalDate, Long> emergencyByDay = data.emergencyBookings.stream()
                .filter(b -> b.getRequestedAt() != null)
                .collect(Collectors.groupingBy(b -> toLocalDate(b.getRequestedAt()), Collectors.counting()));
        Map<LocalDate, Long> appointmentsByDay = data.appointments.stream()
                .filter(a -> a.getAppointmentDateTime() != null)
                .collect(Collectors.groupingBy(a -> toLocalDate(a.getAppointmentDateTime()), Collectors.counting()));

        // One point per day, including zero days, so charts get a continuous x-axis.
        return range.from.datesUntil(range.to.plusDays(1))
                .map(day -> AnalyticsTrendPoint.builder()
                        .date(day)
                        .emergencyBookings(emergencyByDay.getOrDefault(day, 0L))
                        .appointments(appointmentsByDay.getOrDefault(day, 0L))
                        .build())
                .toList();
    }

    @Override
    public AnalyticsSnapshotResponse createSnapshot(String district, String adminId, AnalyticsSnapshotRequest request) {
        DistrictAnalyticsSummary summary = getSummary(district, request.getFromDate(), request.getToDate());
        AnalyticsSnapshot snapshot = AnalyticsSnapshot.builder()
                .district(district)
                .label(request.getLabel())
                .fromDate(summary.getFromDate())
                .toDate(summary.getToDate())
                .summary(summary)
                .createdBy(adminId)
                .createdAt(summary.getGeneratedAt())
                .build();
        return toSnapshotResponse(snapshotRepository.save(snapshot));
    }

    @Override
    public List<AnalyticsSnapshotResponse> getSnapshots(String district) {
        return snapshotRepository.findByDistrictIgnoreCaseOrderByCreatedAtDesc(district).stream()
                .map(this::toSnapshotResponse)
                .toList();
    }

    @Override
    public AnalyticsSnapshotResponse getSnapshot(String district, String snapshotId) {
        return toSnapshotResponse(findOwnSnapshot(district, snapshotId));
    }

    @Override
    public void deleteSnapshot(String district, String snapshotId) {
        snapshotRepository.delete(findOwnSnapshot(district, snapshotId));
    }

    private AnalyticsSnapshot findOwnSnapshot(String district, String snapshotId) {
        AnalyticsSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Analytics snapshot not found with id '" + snapshotId + "'"));
        if (!district.equalsIgnoreCase(snapshot.getDistrict())) {
            throw new ForbiddenException("Analytics snapshot '" + snapshotId + "' belongs to a different district");
        }
        return snapshot;
    }

    private DistrictData load(List<Hospital> hospitals, DateRange range) {
        Set<String> hospitalIds = hospitals.stream().map(Hospital::getId).collect(Collectors.toSet());
        if (hospitalIds.isEmpty()) {
            return new DistrictData(hospitals, hospitalIds, List.of(), List.of(), List.of());
        }
        return new DistrictData(
                hospitals,
                hospitalIds,
                doctorRepository.findByHospitalAssociationsHospitalIdInAndActiveTrue(hospitalIds),
                emergencyBookingRepository.findByHospitalIdsRequestedBetween(hospitalIds, range.start(), range.end()),
                appointmentBookingRepository.findByHospitalIdsScheduledBetween(hospitalIds, range.start(), range.end()));
    }

    private HospitalAnalyticsResponse toHospitalAnalytics(Hospital hospital, DistrictData data) {
        String id = hospital.getId();
        BedCapacity beds = hospital.getFacilities() == null ? null : hospital.getFacilities().getBedCapacity();
        List<EmergencyBooking> emergencies = data.emergencyBookings.stream()
                .filter(b -> id.equals(b.getHospitalId()))
                .toList();
        List<DoctorAppointmentBooking> appointments = data.appointments.stream()
                .filter(a -> id.equals(a.getHospitalId()))
                .toList();

        return HospitalAnalyticsResponse.builder()
                .hospitalId(id)
                .hospitalName(hospital.getHospitalName())
                .city(hospital.getAddress() == null ? null : hospital.getAddress().getCity())
                .hospitalType(hospital.getHospitalType())
                .active(hospital.isActive())
                .handlesEmergencies(hospital.getEmergencyServices() != null
                        && hospital.getEmergencyServices().isHandlesEmergencies())
                .generalBeds(beds == null ? 0 : nullToZero(beds.getGeneral()))
                .icuBeds(beds == null ? 0 : nullToZero(beds.getIcu()))
                .nicuBeds(beds == null ? 0 : nullToZero(beds.getNicu()))
                .associatedDoctors(data.doctors.stream().filter(d -> hasActiveAssociation(d, id)).count())
                .checkedInDoctors(data.doctors.stream().filter(d -> id.equals(d.getCurrentHospitalId())).count())
                .emergencyBookings(emergencies.size())
                .emergencyBookingsByStatus(countByEnum(emergencies, EmergencyBooking::getStatus, EmergencyBookingStatus.class))
                .appointments(appointments.size())
                .appointmentsByStatus(countByEnum(appointments, DoctorAppointmentBooking::getStatus, AppointmentStatus.class))
                .build();
    }

    private AnalyticsSnapshotResponse toSnapshotResponse(AnalyticsSnapshot snapshot) {
        return AnalyticsSnapshotResponse.builder()
                .id(snapshot.getId())
                .district(snapshot.getDistrict())
                .label(snapshot.getLabel())
                .fromDate(snapshot.getFromDate())
                .toDate(snapshot.getToDate())
                .summary(snapshot.getSummary())
                .createdBy(snapshot.getCreatedBy())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }

    private static boolean inDistrict(Hospital hospital, String district) {
        return hospital.getAddress() != null && district.equalsIgnoreCase(hospital.getAddress().getDistrict());
    }

    private static boolean hasActiveAssociation(Doctor doctor, String hospitalId) {
        return doctor.getHospitalAssociations() != null
                && doctor.getHospitalAssociations().stream()
                        .anyMatch(a -> a.isActive() && hospitalId.equals(a.getHospitalId()));
    }

    private static long sumBeds(Collection<Hospital> hospitals, Function<BedCapacity, Integer> field) {
        return hospitals.stream()
                .map(Hospital::getFacilities)
                .filter(Objects::nonNull)
                .map(Facilities::getBedCapacity)
                .filter(Objects::nonNull)
                .mapToLong(beds -> nullToZero(field.apply(beds)))
                .sum();
    }

    private static long countWithFlag(Collection<Hospital> hospitals, Function<EmergencyServices, Boolean> flag) {
        return hospitals.stream()
                .map(Hospital::getEmergencyServices)
                .filter(Objects::nonNull)
                .filter(flag::apply)
                .count();
    }

    // Every enum constant is present (zero when absent) so the response shape is stable.
    private static <T, E extends Enum<E>> Map<E, Long> countByEnum(
            Collection<T> items, Function<T, E> classifier, Class<E> enumType) {
        Map<E, Long> counts = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            counts.put(value, 0L);
        }
        items.stream().map(classifier).filter(Objects::nonNull).forEach(value -> counts.merge(value, 1L, Long::sum));
        return counts;
    }

    private static Map<String, Long> countByEmergencyType(Collection<EmergencyBooking> bookings) {
        return bookings.stream()
                .map(EmergencyBooking::getEmergencyType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static long nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    private record DistrictData(
            List<Hospital> hospitals,
            Set<String> hospitalIds,
            List<Doctor> doctors,
            List<EmergencyBooking> emergencyBookings,
            List<DoctorAppointmentBooking> appointments) {}

    // Inclusive calendar-day range; queried as the half-open instant window [start, end).
    record DateRange(LocalDate from, LocalDate to) {

        static DateRange resolve(LocalDate fromDate, LocalDate toDate) {
            LocalDate to = toDate != null ? toDate : LocalDate.now(ZONE);
            LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_RANGE_DAYS - 1);
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("fromDate must be on or before toDate");
            }
            if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
                throw new IllegalArgumentException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
            }
            return new DateRange(from, to);
        }

        Instant start() {
            return from.atStartOfDay(ZONE).toInstant();
        }

        Instant end() {
            return to.plusDays(1).atStartOfDay(ZONE).toInstant();
        }
    }
}
