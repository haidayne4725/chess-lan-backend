package com.chesslan.game.controller.interfaces;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.model.dto.reward.RewardHistoryResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

public interface RewardControllerApi {
    ResponseEntity<ApiResponse<List<RewardHistoryResponseDTO>>> history(Principal principal, HttpServletRequest request);
}
