package com.chesslan.game.model.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateUserCurrencyRequestDTO(
        @NotNull @Min(0) Long gold
) {
}
