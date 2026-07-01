package com.chesslan.game.controller.interfaces;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.model.dto.reward.RewardHistoryResponseDTO;
import com.chesslan.game.model.dto.reward.BotMatchRewardRequestDTO;
import com.chesslan.game.model.dto.reward.BotMatchRewardResponseDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

public interface RewardControllerApi {
    ResponseEntity<ApiResponse<List<RewardHistoryResponseDTO>>> history(Principal principal, HttpServletRequest request);
    ResponseEntity<ApiResponse<BotMatchRewardResponseDTO>> botMatch(
            @Valid @RequestBody BotMatchRewardRequestDTO body,
            Principal principal,
            HttpServletRequest request
    );
}
