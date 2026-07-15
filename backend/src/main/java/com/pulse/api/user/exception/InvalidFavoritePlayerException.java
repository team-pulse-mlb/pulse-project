package com.pulse.api.user.exception;

/**
 * 존재하지 않거나 올바르지 않은 선수 ID가 요청에 포함됐을 때 발생하는 예외입니다.
 */
public class InvalidFavoritePlayerException
        extends RuntimeException {

    public InvalidFavoritePlayerException(String message) {
        super(message);
    }
}