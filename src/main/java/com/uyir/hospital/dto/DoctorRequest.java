package com.uyir.hospital.dto;

import com.uyir.hospital.model.embedded.Availability;
import com.uyir.hospital.model.embedded.ContactDetails;
import com.uyir.hospital.model.embedded.HospitalAssociation;
import com.uyir.hospital.model.enums.DoctorCategory;
import com.uyir.hospital.model.enums.EngagementType;
import com.uyir.hospital.model.enums.Sex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRequest {

    @NotBlank
    private String name;

    private Integer age;

    @NotNull
    private Sex sex;

    @NotBlank
    private String tnmcNumber;

    private List<String> qualifications;
    private List<String> specialties;
    private Integer yearsOfExperience;
    private BigDecimal consultationFee;

    private Availability availability;

    @NotNull
    private EngagementType engagementType;

    private DoctorCategory doctorCategory;
    private String eSignDocumentUrl;

    @NotNull
    @Valid
    private ContactDetails contactDetails;

    private List<HospitalAssociation> hospitalAssociations;
}
