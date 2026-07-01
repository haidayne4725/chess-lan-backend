package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.controller.interfaces.RewardControllerApi;
import com.chesslan.game.model.dto.reward.RewardHistoryResponseDTO;
import com.chesslan.game.service.interfaces.RewardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardController implements RewardControllerApi {
    private final RewardService rewardService;

    @Override
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<RewardHistoryResponseDTO>>> history(Principal principal,
                                                                               HttpServletRequest request) {
        return ResponseFactory.ok(
                rewardService.history(principal.getName()),
                "Get reward history success",
                request
        );
    }
}
