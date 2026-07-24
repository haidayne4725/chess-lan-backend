package com.chesslan.game.service.interfaces;

import com.chesslan.game.model.dto.room.RoomResponseDTO;

public interface RoomService {
    RoomResponseDTO create(String username);
    RoomResponseDTO create(String username, String gameMode);
    RoomResponseDTO join(String username, String roomCode);
    RoomResponseDTO join(String username, String roomCode, String gameMode);
    RoomResponseDTO findByCode(String roomCode);

    void validateGameMode(String roomCode, String requestedGameMode);
}
