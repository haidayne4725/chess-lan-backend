package com.chesslan.game.controller.interfaces;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.model.dto.room.JoinRoomRequestDTO;
import com.chesslan.game.model.dto.room.CreateRoomRequestDTO;
import com.chesslan.game.model.dto.room.RoomResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;

public interface RoomControllerApi {
    ResponseEntity<ApiResponse<RoomResponseDTO>> create(@Valid @RequestBody(required = false) CreateRoomRequestDTO body,
                                                        Principal principal,
                                                        HttpServletRequest request);
    ResponseEntity<ApiResponse<RoomResponseDTO>> join(@Valid @RequestBody JoinRoomRequestDTO body,
                                                      Principal principal,
                                                      HttpServletRequest request);
    ResponseEntity<ApiResponse<RoomResponseDTO>> findByCode(@PathVariable String roomCode,
                                                            HttpServletRequest request);
}
