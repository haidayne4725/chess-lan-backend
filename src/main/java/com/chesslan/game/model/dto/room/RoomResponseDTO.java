package com.chesslan.game.model.dto.room;

import java.util.UUID;

public record RoomResponseDTO(
        UUID id,
        String roomCode,
        UUID hostId,
        String hostUsername,
        UUID guestId,
        String guestUsername,
        String status
) {
}
