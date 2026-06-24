package com.chesslan.game.infrastructure.websocket;

import com.chesslan.game.common.security.JwtService;
import com.chesslan.game.repository.RoomRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChessHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtService jwtService;
    private final RoomRepository roomRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            String token = params.getFirst("token");
            String roomCode = params.getFirst("roomCode");
            if (token == null || roomCode == null || roomCode.isBlank()) {
                return false;
            }
            Claims claims = jwtService.parse(token);
            var room = roomRepository.findByRoomCodeIgnoreCase(roomCode).orElse(null);
            UUID userId = UUID.fromString(claims.get("uid", String.class));
            boolean belongsToRoom = room != null
                    && (room.getHost().getId().equals(userId)
                    || (room.getGuest() != null && room.getGuest().getId().equals(userId)));
            if (!"access".equals(claims.get("typ", String.class))
                    || claims.getId() == null
                    || !belongsToRoom) {
                return false;
            }
            attributes.put("username", claims.getSubject());
            attributes.put("userId", userId.toString());
            attributes.put("roomCode", roomCode.toUpperCase());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
