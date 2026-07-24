package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.room.RoomResponseDTO;
import com.chesslan.game.model.entity.RoomEntity;
import com.chesslan.game.model.entity.GameMode;
import com.chesslan.game.model.entity.RoomStatus;
import com.chesslan.game.model.entity.UserEntity;
import com.chesslan.game.repository.RoomRepository;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameMapper mapper;

    @Override
    @Transactional
    public RoomResponseDTO create(String username) {
        return create(username, null);
    }

    @Override
    @Transactional
    public RoomResponseDTO create(String username, String gameMode) {
        UserEntity host = requireUser(username);
        RoomEntity room = new RoomEntity();
        room.setRoomCode(generateCode());
        room.setHost(host);
        room.setStatus(RoomStatus.WAITING);
        room.setGameMode(parseGameMode(gameMode));
        return mapper.toRoom(roomRepository.save(room));
    }

    @Override
    @Transactional
    public RoomResponseDTO join(String username, String roomCode) {
        return join(username, roomCode, null);
    }

    @Override
    @Transactional
    public RoomResponseDTO join(String username, String roomCode, String gameMode) {
        UserEntity guest = requireUser(username);
        RoomEntity room = roomRepository.findByRoomCodeIgnoreCase(roomCode.trim())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));
        if (room.getStatus() != RoomStatus.WAITING || room.getGuest() != null) {
            throw new ApiException(ErrorCode.ROOM_NOT_JOINABLE);
        }
        if (room.getHost().getId().equals(guest.getId())) {
            throw new ApiException(ErrorCode.ROOM_NOT_JOINABLE, "Host cannot join the same room as guest");
        }
        requireMatchingMode(room, gameMode);
        room.setGuest(guest);
        room.setStatus(RoomStatus.PLAYING);
        return mapper.toRoom(roomRepository.save(room));
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponseDTO findByCode(String roomCode) {
        return roomRepository.findByRoomCodeIgnoreCase(roomCode)
                .map(mapper::toRoom)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public void validateGameMode(String roomCode, String requestedGameMode) {
        RoomEntity room = roomRepository.findByRoomCodeIgnoreCase(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));
        requireMatchingMode(room, requestedGameMode);
    }

    private GameMode parseGameMode(String value) {
        try {
            return GameMode.fromNullable(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "gameMode must be CLASSIC or ARAM");
        }
    }

    private void requireMatchingMode(RoomEntity room, String requestedGameMode) {
        if (requestedGameMode == null || requestedGameMode.isBlank()) {
            if (room.getGameMode() == GameMode.ARAM) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "ARAM room requires gameMode=ARAM");
            }
            return;
        }
        if (parseGameMode(requestedGameMode) != room.getGameMode()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Room game mode does not match the request");
        }
    }

    private UserEntity requireUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    private String generateCode() {
        String code;
        do {
            StringBuilder value = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                value.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = value.toString();
        } while (roomRepository.existsByRoomCode(code));
        return code;
    }
}
