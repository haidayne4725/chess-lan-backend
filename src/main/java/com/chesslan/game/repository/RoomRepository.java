package com.chesslan.game.repository;

import com.chesslan.game.model.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {
    Optional<RoomEntity> findByRoomCodeIgnoreCase(String roomCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from RoomEntity room where lower(room.roomCode) = lower(:roomCode)")
    Optional<RoomEntity> findByRoomCodeForUpdate(@Param("roomCode") String roomCode);

    boolean existsByRoomCode(String roomCode);
}
