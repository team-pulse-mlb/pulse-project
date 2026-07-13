package com.pulse.api.user.exception;

public class FavoriteTeamLimitExceededException extends RuntimeException {

    public FavoriteTeamLimitExceededException(String message) {
        super(message);
    }
}