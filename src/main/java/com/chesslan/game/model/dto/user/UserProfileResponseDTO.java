package com.chesslan.game.model.dto.user;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponseDTO(
        UUID id,
        String username,
        Integer level,
        Long exp,
        Long nextLevelExp,
        Long gold,
        Integer elo,
        Long totalMatches,
        Long totalWins,
        Long totalLosses,
        Long totalDraws,
        LocalDateTime createdAt
) {
}
