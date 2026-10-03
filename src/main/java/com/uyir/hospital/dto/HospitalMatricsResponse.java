package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Service-wide (all districts) counts. For district-scoped numbers see DistrictAnalyticsSummary.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalMatricsResponse {

    private LocalDate fromDate;
    private LocalDate toDate;
    private Instant generatedAt;

    private HospitalMatrics hospitals;
    private DoctorMatrics doctors;
    private EmergencyBookingMatrics emergencyBookings;
    private AppointmentMatrics appointments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalMatrics {
        private long total;
        private long active;
        private long inactive;
        // Active hospitals that accept emergencies
        private long handlingEmergencies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorMatrics {
        private long total;
        private long active;
        // Active doctors checked into any hospital right now
        private long checkedInNow;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyBookingMatrics {
        // Requested within the date range
        private long total;
        private Map<EmergencyBookingStatus, Long> byStatus;
        // REQUESTED right now, regardless of date range
        private long openNow;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentMatrics {
        // Scheduled within the date range
        private long total;
        private Map<AppointmentStatus, Long> byStatus;
        // CONFIRMED or RESCHEDULED with a time from now on, regardless of date range
        private long upcoming;
    }
}
