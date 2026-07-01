package com.chesslan.game.model.dto.user;

public record UserProgressionResponseDTO(
        Integer level,
        Long currentExp,
        Long nextLevelExp,
        Integer progressPercent
) {
}
