package com.chesslan.game.controller.interfaces;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.model.dto.match.MatchResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

public interface MatchControllerApi {
    ResponseEntity<ApiResponse<List<MatchResponseDTO>>> history(Principal principal,
                                                                HttpServletRequest request);
    ResponseEntity<ApiResponse<MatchResponseDTO>> findById(UUID matchId,
                                                           Principal principal,
                                                           HttpServletRequest request);
    ResponseEntity<ApiResponse<MatchResponseDTO>> active(Principal principal,
                                                         HttpServletRequest request);
}
