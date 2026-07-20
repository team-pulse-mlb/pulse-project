package com.pulse.api.user.exception;

/**
 * 새 비밀번호가 현재 비밀번호와 같을 때 발생합니다.
 */
public class SamePasswordException extends RuntimeException {

    public SamePasswordException(String message) {
        super(message);
    }
}