package com.kosta.darfin.global.exception;

import com.kosta.darfin.dto.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Business exception [{}]: {}", ex.getStatus(), ex.getReason());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ErrorResponse.of(HttpStatus.valueOf(ex.getStatus().value()), ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        if (isClientAbort(ex)) {
            log.debug("Client disconnected: {}", ex.getMessage());
            return null;
        }
        log.error("Unhandled exception: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
    }

    private boolean isClientAbort(Throwable ex) {
        if (ex == null) return false;
        if ("ClientAbortException".equals(ex.getClass().getSimpleName())) return true;
        if (ex instanceof java.io.IOException) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset"))) return true;
        }
        return isClientAbort(ex.getCause());
    }
}
