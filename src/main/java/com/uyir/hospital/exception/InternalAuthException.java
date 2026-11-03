package com.uyir.hospital.exception;

// 401: a service-to-service call with a missing or wrong X-Internal-Api-Key.
public class InternalAuthException extends RuntimeException {

    public InternalAuthException(String message) {
        super(message);
    }
}
