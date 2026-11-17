package com.uyir.hospital.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// hospital.emergency.* - emergency mode switches itself off.
// onDuration: how long emergency mode stays on after a hospital turns it on (or extends it by turning it on again).
// expiryWarningBefore: how long before the switch-off the hospital's live dashboards get a warning.
@ConfigurationProperties("hospital.emergency")
public record EmergencyServicesProperties(
        @DefaultValue("6h") Duration onDuration,
        @DefaultValue("15m") Duration expiryWarningBefore) {
}
