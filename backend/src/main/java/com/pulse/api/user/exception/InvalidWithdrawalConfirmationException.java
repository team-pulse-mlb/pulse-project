package com.pulse.api.user.exception;

/**
 * 회원탈퇴 확인 문구가 일치하지 않을 때 발생합니다.
 */
public class InvalidWithdrawalConfirmationException
        extends RuntimeException {

    public InvalidWithdrawalConfirmationException(
            String message
    ) {
        super(message);
    }
}