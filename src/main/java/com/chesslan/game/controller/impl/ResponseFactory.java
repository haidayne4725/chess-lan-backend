package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

final class ResponseFactory {
    private ResponseFactory() {
    }

    static <T> ResponseEntity<ApiResponse<T>> ok(T result, String message, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .code(1000)
                .message(message)
                .result(result)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }
}
