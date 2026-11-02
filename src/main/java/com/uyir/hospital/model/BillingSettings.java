package com.uyir.hospital.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// The commission rate an admin set. A single document; absent until the first admin change.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "billing_settings")
public class BillingSettings {

    public static final String ID = "commission";

    @Id
    private String id;

    private BigDecimal commissionPerAppointment;
    private Instant updatedAt;
    private String updatedBy;
}
