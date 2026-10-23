package com.uyir.hospital.controller;

import com.uyir.hospital.exception.PaymentUnavailableException;
import com.uyir.hospital.exception.WebhookRejectedException;
import com.uyir.hospital.payment.PaymentWebhookEvent;
import com.uyir.hospital.payment.SeatProperties;
import com.uyir.hospital.payment.WebhookSignature;
import com.uyir.hospital.service.HospitalSeatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// payment-service reports a seat purchase's payment result here. No user token: the call is
// authenticated by an HMAC signature over the raw body (see WebhookSignature). payment-service
// retries until it gets a 2xx, and HospitalSeatService.onPaymentEvent is idempotent.
@RestController
@RequestMapping("/api/v1/hospital/seats/payments")
@Tag(name = "Doctor Seats")
public class SeatPaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SeatPaymentWebhookController.class);

    private final HospitalSeatService hospitalSeatService;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final WebhookSignature signature;

    public SeatPaymentWebhookController(
            HospitalSeatService hospitalSeatService, JsonMapper jsonMapper, Validator validator, SeatProperties props) {
        this.hospitalSeatService = hospitalSeatService;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        SeatProperties.Payment cfg = props.payment();
        this.signature = cfg.webhookSecret() != null && !cfg.webhookSecret().isBlank()
                ? new WebhookSignature(cfg.webhookSecret(), cfg.webhookTolerance(), Clock.systemUTC())
                : null;
    }

    @PostMapping("/webhook")
    public Map<String, Boolean> webhook(
            @RequestHeader(value = "X-Payment-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Payment-Signature", required = false) String sig,
            @RequestBody byte[] raw) {
        if (signature == null) {
            throw new PaymentUnavailableException("Seat payment webhook is not configured (hospital.seat.payment.webhook-secret)");
        }
        if (!signature.isValid(timestamp, sig, raw)) {
            log.warn("Rejected seat payment webhook: bad or expired signature");
            throw new WebhookRejectedException("Bad or expired signature");
        }
        hospitalSeatService.onPaymentEvent(parse(raw));
        return Map.of("ok", true);
    }

    private PaymentWebhookEvent parse(byte[] raw) {
        PaymentWebhookEvent event;
        try {
            event = jsonMapper.readValue(raw, PaymentWebhookEvent.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Malformed webhook body");
        }
        Set<ConstraintViolation<PaymentWebhookEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", ")));
        }
        return event;
    }
}
