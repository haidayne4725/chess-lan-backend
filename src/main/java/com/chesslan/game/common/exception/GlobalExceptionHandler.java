package com.chesslan.game.common.exception;

import com.chesslan.game.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApi(ApiException exception, HttpServletRequest request) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.<Void>builder()
                .code(code.getCode())
                .message(exception.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception,
                                                       HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message(ErrorCode.INVALID_REQUEST.getMessage())
                .errors(errors)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException exception,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus()).body(ApiResponse.<Void>builder()
                .code(ErrorCode.FORBIDDEN.getCode())
                .message(ErrorCode.FORBIDDEN.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(ApiResponse.<Void>builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(ErrorCode.INTERNAL_ERROR.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }
}
