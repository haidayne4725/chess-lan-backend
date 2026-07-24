package com.chesslan.game.model.dto.room;

import jakarta.validation.constraints.Pattern;

public record CreateRoomRequestDTO(
        @Pattern(regexp = "(?i)^(CLASSIC|ARAM)$", message = "gameMode must be CLASSIC or ARAM")
        String gameMode
) {
}
