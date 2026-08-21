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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalResponse {

    private String id;
    private String hospitalName;
    private String registrationNumber;
    private OwnershipType ownershipType;
    private HospitalType hospitalType;
    private Address address;
    private ContactDetails contactDetails;
    private Facilities facilities;
    private EmergencyServices emergencyServices;
    private StaffDetails staffDetails;
    private Operations operations;
    private SurgicalNetwork surgicalNetwork;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
