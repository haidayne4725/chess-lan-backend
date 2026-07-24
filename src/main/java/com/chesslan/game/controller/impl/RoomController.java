package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.controller.interfaces.RoomControllerApi;
import com.chesslan.game.model.dto.room.JoinRoomRequestDTO;
import com.chesslan.game.model.dto.room.CreateRoomRequestDTO;
import com.chesslan.game.model.dto.room.RoomResponseDTO;
import com.chesslan.game.service.interfaces.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController implements RoomControllerApi {
    private final RoomService roomService;

    @Override
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<RoomResponseDTO>> create(@Valid @RequestBody(required = false) CreateRoomRequestDTO body,
                                                               Principal principal,
                                                               HttpServletRequest request) {
        return ResponseFactory.ok(
                roomService.create(principal.getName(), body == null ? null : body.gameMode()),
                "Create room success",
                request
        );
    }

    @Override
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<RoomResponseDTO>> join(@Valid @RequestBody JoinRoomRequestDTO body,
                                                             Principal principal,
                                                             HttpServletRequest request) {
        return ResponseFactory.ok(
                roomService.join(principal.getName(), body.roomCode(), body.gameMode()),
                "Join room success",
                request
        );
    }

    @Override
    @GetMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<RoomResponseDTO>> findByCode(@PathVariable String roomCode,
                                                                   HttpServletRequest request) {
        return ResponseFactory.ok(roomService.findByCode(roomCode), "Get room success", request);
    }
}
