package com.uyir.hospital.model;

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
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "doctors")


public class Doctor {

    @Id
    private String id;

    private String name;
    private Integer age;
    private Sex sex;

    @Indexed(unique = true)
    private String tnmcNumber;

    private List<String> qualifications;
    private List<String> specialties;
    private Integer yearsOfExperience;
    private BigDecimal consultationFee;

    private Availability availability;
    private EngagementType engagementType;

    // Doctors 2 and 3 on a hospital's roster are tagged FREELANCER rather than PRIMARY
    private DoctorCategory doctorCategory;

    // Required for teleconsultation prescriptions; stores the uploaded e-sign document reference
    private String eSignDocumentUrl;

    private ContactDetails contactDetails;
    private List<HospitalAssociation> hospitalAssociations;

    // Live presence: which single hospital this doctor is checked into right now, if any.
    // At most one at a time by construction - see DoctorServiceImpl.checkIn.
    private String currentHospitalId;
    private Instant checkedInAt;

    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;
}
