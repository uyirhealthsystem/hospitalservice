package com.uyir.hospital.exception;

import org.springframework.http.HttpStatus;

// Analytics PIN problems. The response carries `code` so the hospital app knows which screen to show
// (set a PIN, ask for the PIN again, wait out a lock) without parsing the message.
public class AnalyticsPinException extends RuntimeException {

    public enum Code {
        // 403: the hospital hasn't set a PIN yet - analytics stay locked until it does.
        ANALYTICS_PIN_NOT_SET(HttpStatus.FORBIDDEN),
        // 403: the request didn't include the PIN.
        ANALYTICS_PIN_REQUIRED(HttpStatus.FORBIDDEN),
        // 403: wrong PIN (or wrong current PIN when changing it).
        ANALYTICS_PIN_INCORRECT(HttpStatus.FORBIDDEN),
        // 429: too many wrong PINs - locked for a while.
        ANALYTICS_PIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS),
        // 409: a PIN already exists - use change or reset instead.
        ANALYTICS_PIN_ALREADY_SET(HttpStatus.CONFLICT);

        private final HttpStatus status;

        Code(HttpStatus status) {
            this.status = status;
        }
    }

    private final Code code;

    public AnalyticsPinException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return code.status;
    }
}
