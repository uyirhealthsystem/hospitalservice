package com.uyir.hospital.dto;

import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.ContactDetails;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// What a patient sees when opening one of their appointments: the booking itself plus where to go and who
// they'll see. hospital / doctor are null if that record has since been deleted.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDetailResponse {

    private DoctorAppointmentBookingResponse appointment;
    private HospitalInfo hospital;
    private DoctorInfo doctor;
    // The patient can still cancel it (CONFIRMED / RESCHEDULED) - drives the cancel button.
    private boolean cancellable;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HospitalInfo {
        private String id;
        private String hospitalName;
        private Address address;
        private ContactDetails contactDetails;
    }

    // The doctor's own contact details are deliberately left out - the patient reaches them via the hospital.
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
        private BigDecimal consultationFee;
    }
}
