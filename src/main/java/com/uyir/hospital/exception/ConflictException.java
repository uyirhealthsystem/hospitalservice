package com.uyir.hospital.exception;

// 409: the request is valid but clashes with the current state (e.g. no empty seat left).
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
