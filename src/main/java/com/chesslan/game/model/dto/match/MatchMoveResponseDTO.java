package com.chesslan.game.model.dto.match;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchMoveResponseDTO(
        UUID id,
        int moveNumber,
        UUID playerId,
        String playerUsername,
        String from,
        String to,
        String promotion,
        String notation,
        String fenAfter,
        LocalDateTime playedAt
) {
}
