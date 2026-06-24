package com.chesslan.game.model.dto.match;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MatchResponseDTO(
        UUID id,
        String roomCode,
        UUID whitePlayerId,
        String whiteUsername,
        UUID blackPlayerId,
        String blackUsername,
        UUID winnerId,
        String winnerUsername,
        String status,
        String terminationReason,
        String currentFen,
        int moveCount,
        int whiteEloBefore,
        Integer whiteEloAfter,
        int blackEloBefore,
        Integer blackEloAfter,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<MatchMoveResponseDTO> moves
) {
}
