package com.uyir.hospital.payment;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

// hospital.billing.* in application.properties. defaultCommissionPerAppointment applies until an admin
// sets a rate (PUT /api/v1/hospital/billing/settings); after that the stored rate wins.
@ConfigurationProperties("hospital.billing")
public record BillingProperties(BigDecimal defaultCommissionPerAppointment, String currency, int dueDays) {
}
