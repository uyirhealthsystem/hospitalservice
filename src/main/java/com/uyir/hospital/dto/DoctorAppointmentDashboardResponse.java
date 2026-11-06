package com.uyir.hospital.dto;

import com.uyir.hospital.model.embedded.Availability;
import com.uyir.hospital.model.embedded.ContactDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Hospital dashboard for one of its doctors: who the doctor is, how many appointments they got at this
// hospital, how many are done and how many are still to be seen, plus the appointments themselves.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAppointmentDashboardResponse {

    private DoctorInfo doctor;
    // The IST date range the counts cover (inclusive); both null = all time.
    private LocalDate from;
    private LocalDate to;
    private Summary summary;
    // Oldest first.
    private List<DoctorAppointmentBookingResponse> appointments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoctorInfo {
        private String id;
        private String name;
        private List<String> qualifications;
        private List<String> specialties;
        private Integer yearsOfExperience;
        // This hospital's department and fee for the doctor (fee falls back to the doctor's own fee).
        private String department;
        private BigDecimal consultationFee;
        private Availability availability;
        private ContactDetails contactDetails;
        // Holds one of this hospital's doctor seats (only seated doctors can be booked).
        private boolean seated;
        // Currently checked in at this hospital.
        private boolean checkedIn;
        private Instant checkedInAt;
        private boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        // Every appointment booked with the doctor at this hospital (= completed + remaining + noShow + cancelled).
        private long totalAppointments;
        private long completed;
        // Still to be seen: CONFIRMED or RESCHEDULED (includes past ones the hospital has not closed out yet).
        private long remaining;
        private long noShow;
        private long cancelled;
    }
}
