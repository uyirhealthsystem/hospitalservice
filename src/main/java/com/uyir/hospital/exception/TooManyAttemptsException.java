package com.uyir.hospital.exception;

// 429: the caller has to wait or get a new code first - too many wrong claim codes, or a claim code
// resend requested again too soon.
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
