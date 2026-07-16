package com.pulse.api.user.exception;

/**
 * 사용자가 허용된 개수보다 많은 관심 선수를 선택했을 때 발생하는 예외입니다.
 */
public class FavoritePlayerLimitExceededException
        extends RuntimeException {

    public FavoritePlayerLimitExceededException(String message) {
        super(message);
    }
}