package com.chesslan.game.infrastructure.websocket;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.service.interfaces.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChessWebSocketHandler extends TextWebSocketHandler {
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "READY", "MOVE", "RESIGN", "DRAW_OFFER", "DRAW_ACCEPT", "SYNC_REQUEST"
    );

    private final JsonMapper objectMapper;
    private final MatchService matchService;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> readyPlayers = new ConcurrentHashMap<>();
    private final Set<String> startedRooms = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        roomSessions.computeIfAbsent(roomCode(session), ignored -> ConcurrentHashMap.newKeySet()).add(session);
        broadcastEvent(roomCode(session), "PLAYER_JOINED", null, Map.of(
                "userId", userId(session),
                "username", username(session)
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode incoming = objectMapper.readTree(message.getPayload());
            String type = incoming.path("type").asText("").toUpperCase();
            String requestId = incoming.path("requestId").asText(UUID.randomUUID().toString());
            JsonNode payload = incoming.has("payload") ? incoming.path("payload") : incoming;

            if (!SUPPORTED_EVENTS.contains(type)) {
                sendError(session, requestId, "UNSUPPORTED_EVENT", "Unsupported message type");
                return;
            }

            switch (type) {
                case "READY" -> handleReady(session, requestId);
                case "MOVE" -> handleMove(session, requestId, payload);
                case "RESIGN" -> broadcastEvent(roomCode(session), "GAME_OVER", requestId,
                        matchService.resign(username(session), roomCode(session)));
                case "DRAW_OFFER" -> broadcastEvent(roomCode(session), "DRAW_OFFERED", requestId,
                        matchService.offerDraw(username(session), roomCode(session)));
                case "DRAW_ACCEPT" -> broadcastEvent(roomCode(session), "GAME_OVER", requestId,
                        matchService.acceptDraw(username(session), roomCode(session)));
                case "SYNC_REQUEST" -> sendEvent(session, "GAME_STATE", requestId,
                        matchService.syncState(username(session), roomCode(session)));
                default -> sendError(session, requestId, "UNSUPPORTED_EVENT", "Unsupported message type");
            }
        } catch (ApiException exception) {
            sendError(session, null, exception.getErrorCode().name(), exception.getMessage());
        } catch (Exception exception) {
            sendError(session, null, "INVALID_MESSAGE", "Message must be valid JSON");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<WebSocketSession> sessions = roomSessions.get(roomCode(session));
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomCode(session));
                readyPlayers.remove(roomCode(session));
                startedRooms.remove(roomCode(session));
            }
        }
        broadcastEvent(roomCode(session), "PLAYER_DISCONNECTED", null, Map.of(
                "userId", userId(session),
                "username", username(session)
        ));
    }

    private void handleReady(WebSocketSession session, String requestId) {
        Set<String> ready = readyPlayers.computeIfAbsent(
                roomCode(session),
                ignored -> ConcurrentHashMap.newKeySet()
        );
        ready.add(userId(session));
        broadcastEvent(roomCode(session), "PLAYER_READY", requestId, Map.of(
                "userId", userId(session),
                "username", username(session),
                "readyCount", ready.size()
        ));
        if (ready.size() >= 2 && startedRooms.add(roomCode(session))) {
            try {
                Map<String, Object> game = matchService.startMatch(roomCode(session));
                broadcastEvent(roomCode(session), "GAME_START", requestId, game);
            } catch (RuntimeException exception) {
                startedRooms.remove(roomCode(session));
                throw exception;
            }
        }
    }

    private void handleMove(WebSocketSession session, String requestId, JsonNode payload) {
        Map<String, Object> result = matchService.submitMove(
                username(session),
                roomCode(session),
                requestId,
                payload.path("from").asText(null),
                payload.path("to").asText(null),
                payload.path("promotion").asText(null)
        );
        broadcastEvent(roomCode(session), "MOVE_RESULT", requestId, result);
        if (!"ACTIVE".equals(result.get("status"))) {
            Object gameOver = result.get("gameOver");
            if (gameOver instanceof Map<?, ?> map) {
                broadcastEvent(roomCode(session), "GAME_OVER", requestId, map);
            }
        }
    }

    private void broadcastEvent(String roomCode, String type, String requestId, Map<?, ?> payload) {
        String json = eventJson(roomCode, type, requestId, payload);
        roomSessions.getOrDefault(roomCode, Set.of()).forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception ignored) {
                    // Closed sessions are removed by the close callback.
                }
            }
        });
    }

    private void sendEvent(WebSocketSession session, String type, String requestId, Map<?, ?> payload) {
        try {
            session.sendMessage(new TextMessage(eventJson(roomCode(session), type, requestId, payload)));
        } catch (Exception ignored) {
            // The client disconnected before the response could be written.
        }
    }

    private void sendError(WebSocketSession session, String requestId, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        sendEvent(session, "ERROR", requestId, payload);
    }

    private String eventJson(String roomCode, String type, String requestId, Map<?, ?> payload) {
        try {
            var event = objectMapper.createObjectNode();
            event.put("type", type);
            if (requestId != null) {
                event.put("requestId", requestId);
            }
            event.put("roomCode", roomCode);
            event.put("sentAt", Instant.now().toString());
            event.set("payload", objectMapper.valueToTree(payload));
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            return "{\"type\":\"ERROR\",\"payload\":{\"code\":\"SERIALIZATION_ERROR\"}}";
        }
    }

    private String roomCode(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get("roomCode"));
    }

    private String username(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get("username"));
    }

    private String userId(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get("userId"));
    }
}
