package com.uyir.hospital.ambulance;

import com.uyir.hospital.exception.AmbulanceUnavailableException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// GET {baseUrl}/api/v1/ambulance/internal/bookings/{id}/track
//   X-Internal-Key: <hospital.ambulance.internal-key>   (no user token: the hospital's access is checked here,
//                                                         against the emergency booking, before calling)
@Component
public class HttpAmbulanceClient implements AmbulanceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAmbulanceClient.class);
    private static final String UNAVAILABLE = "Ambulance tracking is unavailable right now, please retry";

    private final RestClient http;
    private final String internalKey;

    public HttpAmbulanceClient(AmbulanceProperties props) {
        this.internalKey = props.internalKey();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (props.connectTimeout() != null) {
            factory.setConnectTimeout(props.connectTimeout());
        }
        if (props.readTimeout() != null) {
            factory.setReadTimeout(props.readTimeout());
        }
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        this.http = StringUtils.hasText(props.baseUrl()) ? builder.baseUrl(props.baseUrl()).build() : null;
    }

    @Override
    public Optional<AmbulanceTrack> track(String ambulanceBookingId) {
        if (http == null || !StringUtils.hasText(internalKey)) {
            log.warn("hospital.ambulance.base-url / internal-key not set: cannot read ambulance {}", ambulanceBookingId);
            throw new AmbulanceUnavailableException(UNAVAILABLE);
        }
        try {
            AmbulanceTrack track = http.get()
                    .uri("/api/v1/ambulance/internal/bookings/{id}/track", ambulanceBookingId)
                    .header("X-Internal-Key", internalKey)
                    .retrieve()
                    .body(AmbulanceTrack.class);
            if (track == null || track.booking() == null) {
                log.error("ambulance service: incomplete tracking response for {}", ambulanceBookingId);
                throw new AmbulanceUnavailableException(UNAVAILABLE);
            }
            return Optional.of(track);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // 400: not a UUID; 404: no such booking. Either way there is no ride to show.
            if (status == 404 || status == 400) {
                return Optional.empty();
            }
            log.warn("ambulance service track {} failed: HTTP {}", ambulanceBookingId, status);
            throw new AmbulanceUnavailableException(UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("ambulance service track {} failed: {}", ambulanceBookingId, e.getMessage());
            throw new AmbulanceUnavailableException(UNAVAILABLE);
        }
    }
}
