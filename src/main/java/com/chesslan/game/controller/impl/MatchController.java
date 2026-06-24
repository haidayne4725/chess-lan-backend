package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.controller.interfaces.MatchControllerApi;
import com.chesslan.game.model.dto.match.MatchResponseDTO;
import com.chesslan.game.service.interfaces.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController implements MatchControllerApi {
    private final MatchService matchService;

    @Override
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<MatchResponseDTO>>> history(Principal principal,
                                                                       HttpServletRequest request) {
        return ResponseFactory.ok(matchService.history(principal.getName()), "Get match history success", request);
    }

    @Override
    @GetMapping("/{matchId}")
    public ResponseEntity<ApiResponse<MatchResponseDTO>> findById(@PathVariable UUID matchId,
                                                                  Principal principal,
                                                                  HttpServletRequest request) {
        return ResponseFactory.ok(
                matchService.findById(principal.getName(), matchId),
                "Get match detail success",
                request
        );
    }

    @Override
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<MatchResponseDTO>> active(Principal principal,
                                                                 HttpServletRequest request) {
        return ResponseFactory.ok(matchService.active(principal.getName()), "Get active match success", request);
    }
}
