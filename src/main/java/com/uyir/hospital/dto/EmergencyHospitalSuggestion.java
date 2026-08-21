package com.uyir.hospital.dto;

import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.ContactDetails;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyHospitalSuggestion {

    private String hospitalId;
    private String hospitalName;
    private Address address;
    private ContactDetails contactDetails;

    // Doctors currently checked in at this hospital (Doctor.currentHospitalId) who are active.
    // A suggestion only exists at all when this is > 0.
    private int availableDoctorCount;
    private List<String> availableSpecialties;
}
