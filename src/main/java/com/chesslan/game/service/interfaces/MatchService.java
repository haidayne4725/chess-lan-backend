package com.chesslan.game.service.interfaces;

import com.chesslan.game.model.dto.match.MatchResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MatchService {
    Map<String, Object> startMatch(String roomCode);
    Map<String, Object> startMatch(String roomCode, String gameMode);
    Map<String, Object> submitMove(String username, String roomCode, String requestId,
                                   String from, String to, String promotion);
    Map<String, Object> submitMove(String username, String roomCode, String requestId,
                                   String from, String to, String promotion, String gameMode);
    Map<String, Object> resign(String username, String roomCode);
    Map<String, Object> offerDraw(String username, String roomCode);
    Map<String, Object> acceptDraw(String username, String roomCode);
    Map<String, Object> syncState(String username, String roomCode);
    Map<String, Object> syncState(String username, String roomCode, String gameMode);
    List<MatchResponseDTO> history(String username);
    MatchResponseDTO findById(String username, UUID matchId);
    MatchResponseDTO active(String username);

}
