package com.chesslan.game.service.interfaces;

import com.chesslan.game.model.dto.room.RoomResponseDTO;

public interface RoomService {
    RoomResponseDTO create(String username);
    RoomResponseDTO join(String username, String roomCode);
    RoomResponseDTO findByCode(String roomCode);
}
