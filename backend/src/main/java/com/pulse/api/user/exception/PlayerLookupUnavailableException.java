package com.pulse.api.user.exception;

/**
 * 관심 선수 등록 과정에서 외부 선수 정보를
 * 일시적으로 확인할 수 없는 경우 발생하는 예외입니다.
 *
 * 이 예외는 "존재하지 않는 선수 ID"와 구분합니다.
 *
 * 예:
 * - balldontlie API 요청 시간 초과
 * - 네트워크 연결 실패
 * - 외부 API 요청 제한(429)
 * - 외부 API 서버 장애
 *
 * 사용자가 잘못된 값을 입력한 문제가 아니므로
 * HTTP 400이 아니라 503 Service Unavailable로 응답합니다.
 */
public class PlayerLookupUnavailableException
        extends RuntimeException {

    public PlayerLookupUnavailableException(
            String message
    ) {
        super(message);
    }

    /**
     * 원래 발생한 외부 API 예외도 함께 보존합니다.
     *
     * 로그에서 실제 장애 원인을 추적할 수 있도록
     * cause를 전달받는 생성자를 제공합니다.
     */
    public PlayerLookupUnavailableException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}