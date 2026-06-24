package com.chesslan.game.repository;

import com.chesslan.game.model.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {
    Optional<RoomEntity> findByRoomCodeIgnoreCase(String roomCode);
    boolean existsByRoomCode(String roomCode);
}
