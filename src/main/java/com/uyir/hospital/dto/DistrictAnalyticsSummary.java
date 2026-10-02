package com.uyir.hospital.dto;

import com.uyir.hospital.model.enums.AppointmentStatus;
import com.uyir.hospital.model.enums.EmergencyBookingStatus;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictAnalyticsSummary {

    private String district;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Instant generatedAt;

    private HospitalStats hospitals;
    private BedStats beds;
    private DoctorStats doctors;
    private EmergencyBookingStats emergencyBookings;
    private AppointmentStats appointments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalStats {
        private long total;
        private long active;
        private long inactive;
        private long handlingEmergencies;
        private long withAmbulance;
        private long withAmbulance24x7;
        private Map<HospitalType, Long> byType;
        private Map<OwnershipType, Long> byOwnership;
    }

    // Active hospitals only - an inactive hospital's beds aren't available capacity.
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BedStats {
        private long general;
        private long icu;
        private long nicu;
        private long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorStats {
        // Distinct active doctors associated with at least one hospital in the district
        private long associated;
        // Distinct active doctors checked into a hospital in the district right now
        private long checkedInNow;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyBookingStats {
        private long total;
        private Map<EmergencyBookingStatus, Long> byStatus;
        // Most frequent first
        private Map<String, Long> byEmergencyType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentStats {
        private long total;
        private Map<AppointmentStatus, Long> byStatus;
    }
}
