package com.uyir.hospital.event;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// hospital.events.* in application.properties.
// provider: "kafka" publishes to `topic` (notification-service emails the hospital, every pod feeds its
// dashboards); "local" feeds this pod's dashboards only (single-instance local dev).
// sseTimeout: how long one dashboard stream stays open before the client has to reconnect.
@ConfigurationProperties("hospital.events")
public record HospitalEventProperties(String provider, String topic, Duration sseTimeout) {
}
