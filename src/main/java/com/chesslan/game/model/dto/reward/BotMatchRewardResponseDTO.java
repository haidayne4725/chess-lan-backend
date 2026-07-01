package com.chesslan.game.model.dto.reward;

public record BotMatchRewardResponseDTO(
        Long expAwarded,
        Long goldAwarded,
        Long totalExp,
        Long totalGold,
        Integer level
) {
}
