package com.uyir.hospital.live;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

// hospital.live.* - the live-updates WebSocket (/api/v1/hospital/ws).
// allowedOrigins: browser Origin patterns allowed to connect ("*" = any; the gateway is the auth perimeter).
// sendTimeLimit / sendBufferLimit: a client that stops reading (bad mobile network) is dropped once one send
// takes longer than this or this many bytes are queued for it - it reconnects - so it can't pile up memory here.
// maxSessionsPerUser: open connections one hospital / patient may hold on one pod (tabs, phones); more are refused.
// (The 10s ping interval is fixed in LiveUpdateHub: it must stay under the gateway's 15s idle timeout.)
@ConfigurationProperties("hospital.live")
public record LiveProperties(
        @DefaultValue("*") List<String> allowedOrigins,
        @DefaultValue("10s") Duration sendTimeLimit,
        @DefaultValue("512KB") DataSize sendBufferLimit,
        @DefaultValue("20") int maxSessionsPerUser) {
}
