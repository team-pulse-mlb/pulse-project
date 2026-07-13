package com.pulse.api.user.exception;

public class EmailVerificationException extends RuntimeException {

    public EmailVerificationException(String message) {
        super(message);
    }
}