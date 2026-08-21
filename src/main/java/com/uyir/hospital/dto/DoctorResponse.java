package com.uyir.hospital.dto;

import com.uyir.hospital.model.embedded.Availability;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.model.enums.DoctorCategory;
import com.uyir.hospital.model.enums.EngagementType;
import com.uyir.hospital.model.enums.Sex;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorResponse {

    private String id;
    private String name;
    private Integer age;
    private Sex sex;
    private String tnmcNumber;
    private List<String> qualifications;
    private List<String> specialties;
    private Integer yearsOfExperience;
    private BigDecimal consultationFee;
    private Availability availability;
    private EngagementType engagementType;
    private DoctorCategory doctorCategory;
    private String eSignDocumentUrl;
    private ContactDetails contactDetails;
    private List<HospitalAssociation> hospitalAssociations;
    private String currentHospitalId;
    private Instant checkedInAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
