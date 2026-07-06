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
                0,
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
                0,
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
                0,
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
                0,
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
                0,
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
                0,
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }


}