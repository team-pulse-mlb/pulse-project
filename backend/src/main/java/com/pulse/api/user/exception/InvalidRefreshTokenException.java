package com.pulse.api.user.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}