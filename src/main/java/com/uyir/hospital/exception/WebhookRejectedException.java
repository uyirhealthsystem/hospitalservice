package com.uyir.hospital.exception;

// 401: a payment-service callback with a missing, wrong or expired signature.
public class WebhookRejectedException extends RuntimeException {

    public WebhookRejectedException(String message) {
        super(message);
    }
}
