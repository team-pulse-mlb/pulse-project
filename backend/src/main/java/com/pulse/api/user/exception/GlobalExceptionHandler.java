package com.pulse.api.user.exception;

import com.pulse.api.user.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice   // 모든 Controller에서 발생하는 예외를 공통으로 받아서 JSON 응답으로 바꿔주는 역할
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)    // DuplicateEmailException이 발생하면 이 메서드가 처리하라는 뜻
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            DuplicateEmailException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "DUPLICATE_EMAIL",
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)    // 409 Conflict, 이미 존재하는 이메일과 충돌했다는 의미
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값을 확인해 주세요.");

        ErrorResponse response = new ErrorResponse(
                "INVALID_REQUEST",
                message
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    // @RequestParam 검증이 실패하면 ConstraintViolationException이 발생 대비
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("입력값을 확인해 주세요.");

        ErrorResponse response = new ErrorResponse(
                "INVALID_REQUEST",
                message
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    // 이메일 관련 예외 처리 메서드
    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<ErrorResponse> handleEmailVerification(
            EmailVerificationException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "EMAIL_VERIFICATION_FAILED",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    // 로그인 이메일 또는 비밀번호 불일치
    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ErrorResponse> handleLoginFailed(
            LoginFailedException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "LOGIN_FAILED",
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED) // 401 Unauthorized
                .body(response);
    }

    //  토큰  발급 오류 처리
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "INVALID_REFRESH_TOKEN",
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }


    // 관심팀 ID가 잘못되었거나 존재하지 않는 팀이 포함된 경우
    @ExceptionHandler(InvalidFavoriteTeamException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFavoriteTeam(
            InvalidFavoriteTeamException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "INVALID_FAVORITE_TEAM",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    // 관심팀 선택 개수가 정책상 허용 범위를 넘은 경우
    @ExceptionHandler(FavoriteTeamLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleFavoriteTeamLimitExceeded(
            FavoriteTeamLimitExceededException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "TOO_MANY_FAVORITE_TEAMS",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    /**
     * 관심 선수 ID가 올바르지 않거나,
     * players 테이블에 존재하지 않는 선수가 포함된 경우 처리합니다.
     */
    @ExceptionHandler(InvalidFavoritePlayerException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFavoritePlayer(
            InvalidFavoritePlayerException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "INVALID_FAVORITE_PLAYER",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    /**
     * 외부 선수 API 장애로 관심 선수 정보를
     * 일시적으로 확인할 수 없는 경우 처리합니다.
     *
     * 사용자가 잘못된 선수 ID를 입력한 상황과 다르므로
     * 400 Bad Request가 아니라 503 Service Unavailable을 반환합니다.
     */
    @ExceptionHandler(PlayerLookupUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePlayerLookupUnavailable(
            PlayerLookupUnavailableException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "PLAYER_LOOKUP_UNAVAILABLE",
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }


    /**
     * 관심 선수 선택 개수가 최대 허용 개수인 5명을 초과한 경우 처리합니다.
     */
    @ExceptionHandler(FavoritePlayerLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleFavoritePlayerLimitExceeded(
            FavoritePlayerLimitExceededException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "TOO_MANY_FAVORITE_PLAYERS",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    /**
     * 비밀번호 변경 요청에서 입력한 현재 비밀번호가
     * DB에 저장된 비밀번호와 일치하지 않는 경우 처리합니다.
     */
    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCurrentPassword(
            InvalidCurrentPasswordException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "INVALID_CURRENT_PASSWORD",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    /**
     * 새 비밀번호와 새 비밀번호 확인 값이
     * 서로 일치하지 않는 경우 처리합니다.
     */
    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePasswordMismatch(
            PasswordMismatchException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                "PASSWORD_MISMATCH",
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }
}