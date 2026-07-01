package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health(HttpServletRequest request) {
        return ResponseFactory.ok(
                Map.of("status", "UP", "serverTime", Instant.now()),
                "Server is running",
                request
        );
    }
}
