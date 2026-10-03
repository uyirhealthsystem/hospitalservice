package com.uyir.hospital.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Service-wide counts, or one district's when 'district' is set.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalMetricsResponse {

    // Only set on the district endpoint; omitted from the service-wide response
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String district;

    private LocalDate fromDate;
    private LocalDate toDate;
    private Instant generatedAt;

    private HospitalMetrics hospitals;
    private DoctorMetrics doctors;
    private EmergencyBookingMetrics emergencyBookings;
    private AppointmentMetrics appointments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalMetrics {
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
    public static class DoctorMetrics {
        private long total;
        private long active;
        // Active doctors checked into any hospital right now
        private long checkedInNow;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyBookingMetrics {
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
    public static class AppointmentMetrics {
        // Scheduled within the date range
        private long total;
        private Map<AppointmentStatus, Long> byStatus;
        // CONFIRMED or RESCHEDULED with a time from now on, regardless of date range
        private long upcoming;
    }
}
