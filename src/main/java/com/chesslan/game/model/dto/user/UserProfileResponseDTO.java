package com.chesslan.game.model.dto.user;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponseDTO(
        UUID id,
        String username,
        Integer elo,
        LocalDateTime createdAt
) {
}
