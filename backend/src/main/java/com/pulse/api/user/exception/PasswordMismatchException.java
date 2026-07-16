package com.pulse.api.user.exception;

/**
 * 새 비밀번호와 새 비밀번호 확인 값이
 * 서로 일치하지 않을 때 발생하는 예외입니다.
 */
public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException(String message) {
        super(message);
    }
}