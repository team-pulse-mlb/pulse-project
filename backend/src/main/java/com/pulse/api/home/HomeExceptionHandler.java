package com.pulse.api.home;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = HomeGameController.class)
public class HomeExceptionHandler {

    @ExceptionHandler(InvalidSlateDateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSlateDate(InvalidSlateDateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_DATE", exception.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
    }
}
