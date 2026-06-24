package com.chesslan.game.model.dto.auth;

import com.chesslan.game.model.dto.user.UserProfileResponseDTO;

public record AuthResponseDTO(
        String token,
        long expiresInSeconds,
        UserProfileResponseDTO user
) {
}
