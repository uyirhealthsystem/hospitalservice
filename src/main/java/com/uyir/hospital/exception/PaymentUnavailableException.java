package com.uyir.hospital.exception;

// 503: payment-service could not be reached or refused the call; the client can retry.
public class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String message) {
        super(message);
    }
}
