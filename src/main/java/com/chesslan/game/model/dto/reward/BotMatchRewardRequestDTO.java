package com.chesslan.game.model.dto.reward;

import jakarta.validation.constraints.NotNull;

public record BotMatchRewardRequestDTO(
        @NotNull BotDifficulty difficulty,
        @NotNull BotMatchResult result
) {
}
