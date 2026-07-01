package com.chesslan.game.model.dto.reward;

public record RewardHistoryResponseDTO(
        String type,
        Long amount,
        String description
) {
}
