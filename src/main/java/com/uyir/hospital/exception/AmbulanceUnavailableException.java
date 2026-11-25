package com.uyir.hospital.exception;

// 503: the ambulance service could not be reached or isn't configured; the client can retry.
public class AmbulanceUnavailableException extends RuntimeException {

    public AmbulanceUnavailableException(String message) {
        super(message);
    }
}
