package com.chesslan.game.model.dto.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinRoomRequestDTO(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "Room code must contain 6 letters or numbers")
        String roomCode
) {
}
