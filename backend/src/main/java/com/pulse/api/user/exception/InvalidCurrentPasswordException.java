package com.pulse.api.user.exception;

/**
 * 비밀번호 변경 요청에서
 * 사용자가 입력한 현재 비밀번호가 실제 비밀번호와 다를 때 발생합니다.
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException(String message) {
        super(message);
    }
}