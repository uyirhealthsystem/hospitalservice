package com.uyir.hospital.dto;

import com.uyir.hospital.model.embedded.Address;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.embedded.EmergencyServices;
import com.uyir.hospital.model.embedded.Facilities;
import com.uyir.hospital.model.embedded.Operations;
import com.uyir.hospital.model.embedded.StaffDetails;
import com.uyir.hospital.model.embedded.SurgicalNetwork;
import com.uyir.hospital.model.enums.HospitalType;
import com.uyir.hospital.model.enums.OwnershipType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalRequest {

    @NotBlank
    private String hospitalName;

    @NotBlank
    private String registrationNumber;

    @NotNull
    private OwnershipType ownershipType;

    @NotNull
    private HospitalType hospitalType;

    @NotNull
    @Valid
    private Address address;

    @NotNull
    @Valid
    private ContactDetails contactDetails;

    private Facilities facilities;
    private EmergencyServices emergencyServices;
    private StaffDetails staffDetails;
    private Operations operations;
    private SurgicalNetwork surgicalNetwork;
}
