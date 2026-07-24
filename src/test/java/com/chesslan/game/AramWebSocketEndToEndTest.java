package com.chesslan.game;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AramWebSocketEndToEndTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @RepeatedTest(3)
    void twoRealClientsCanPlayRejectIllegalMoveDeduplicateAndReconnect() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Session host = signup("e2e_host_" + suffix);
        Session guest = signup("e2e_guest_" + suffix);
        Session outsider = signup("e2e_outsider_" + suffix);

        assertThat(postRaw("/api/rooms/create", host.token(), "{\"gameMode\":\"BLITZ\"}").statusCode())
                .isEqualTo(400);
        JsonNode created = post("/api/rooms/create", host.token(), "{\"gameMode\":\"ARAM\"}");
        String roomCode = created.path("result").path("roomCode").asText();
        assertThat(created.path("result").path("gameMode").asText()).isEqualTo("ARAM");

        assertThat(postRaw("/api/rooms/join", guest.token(),
                "{\"roomCode\":\"" + roomCode + "\"}").statusCode()).isEqualTo(400);
        assertThat(postRaw("/api/rooms/join", guest.token(),
                "{\"roomCode\":\"" + roomCode + "\",\"gameMode\":\"CLASSIC\"}").statusCode())
                .isEqualTo(400);
        JsonNode joined = post("/api/rooms/join", guest.token(),
                "{\"roomCode\":\"" + roomCode + "\",\"gameMode\":\"ARAM\"}");
        assertThat(joined.path("result").path("guestUsername").asText()).isEqualTo(guest.username());

        assertThatThrownBy(() -> connect(outsider.token(), roomCode))
                .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class);

        SocketProbe hostSocket = connect(host.token(), roomCode);
        SocketProbe guestSocket = connect(guest.token(), roomCode);
        SocketProbe hostReplica = null;
        try {
            hostSocket.send("READY", "wrong-mode", "{}");
            JsonNode wrongMode = hostSocket.await(event("ERROR", "wrong-mode"));
            assertThat(wrongMode.path("payload").path("code").asText()).isEqualTo("INVALID_REQUEST");
            assertThat(wrongMode.path("payload").path("message").asText()).contains("gameMode=ARAM");

            hostSocket.send("READY", "ready-host", "{\"gameMode\":\"ARAM\"}");
            guestSocket.send("READY", "ready-guest", "{\"gameMode\":\"ARAM\"}");
            JsonNode hostStart = hostSocket.await(event("GAME_START", null));
            JsonNode guestStart = guestSocket.await(event("GAME_START", null));
            assertSameAramStart(hostStart, guestStart);

            String matchId = hostStart.path("payload").path("matchId").asText();
            hostReplica = connect(host.token(), roomCode);
            String firstMovePayload = "{\"from\":\"e2\",\"to\":\"e4\",\"gameMode\":\"ARAM\"}";
            var firstSend = hostSocket.sendAsync("MOVE", "white-1", firstMovePayload);
            var duplicateSend = hostReplica.sendAsync("MOVE", "white-1", firstMovePayload);
            firstSend.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            duplicateSend.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            JsonNode whiteResult = guestSocket.await(event("MOVE_RESULT", "white-1"));
            JsonNode whiteDuplicate = guestSocket.await(event("MOVE_RESULT", "white-1"));
            assertThat(List.of(
                    whiteResult.path("payload").path("newlyProcessed").asBoolean(),
                    whiteDuplicate.path("payload").path("newlyProcessed").asBoolean()
            )).containsExactlyInAnyOrder(true, false);
            assertThat(whiteResult.path("payload").path("accepted").asBoolean()).isTrue();
            assertThat(whiteResult.path("payload").path("aramState"))
                    .isEqualTo(whiteDuplicate.path("payload").path("aramState"));
            hostReplica.close();
            hostReplica = null;

            guestSocket.send("MOVE", "illegal-black",
                    "{\"from\":\"e7\",\"to\":\"e3\",\"gameMode\":\"ARAM\"}");
            JsonNode illegal = guestSocket.await(event("ERROR", "illegal-black"));
            assertThat(illegal.path("payload").path("code").asText()).isEqualTo("ILLEGAL_MOVE");

            guestSocket.send("MOVE", "black-1",
                    "{\"from\":\"e7\",\"to\":\"e5\",\"gameMode\":\"ARAM\"}");
            JsonNode blackResult = guestSocket.await(event("MOVE_RESULT", "black-1"));
            hostSocket.await(event("MOVE_RESULT", "black-1"));
            assertThat(blackResult.path("payload").path("moveNumber").asInt()).isEqualTo(2);
            assertThat(blackResult.path("payload").path("newlyProcessed").asBoolean()).isTrue();

            hostSocket.send("MOVE", "white-2",
                    "{\"from\":\"d2\",\"to\":\"d3\",\"gameMode\":\"ARAM\"}");
            JsonNode whiteSecond = hostSocket.await(event("MOVE_RESULT", "white-2"));
            guestSocket.await(event("MOVE_RESULT", "white-2"));
            assertThat(whiteSecond.path("payload").path("moveNumber").asInt()).isEqualTo(3);

            guestSocket.send("MOVE", "black-1",
                    "{\"from\":\"e7\",\"to\":\"e5\",\"gameMode\":\"ARAM\"}");
            JsonNode duplicate = guestSocket.await(event("MOVE_RESULT", "black-1"));
            assertThat(duplicate.path("payload").path("newlyProcessed").asBoolean()).isFalse();
            assertThat(duplicate.path("payload").path("moveNumber").asInt()).isEqualTo(2);
            assertThat(duplicate.path("payload").path("fen"))
                    .as("A late duplicate response must not roll clients back to an old board")
                    .isEqualTo(whiteSecond.path("payload").path("fen"));

            hostSocket.send("START", "explicit-start", "{\"gameMode\":\"ARAM\"}");
            JsonNode repeatedStart = hostSocket.await(event("GAME_START", "explicit-start"));
            assertThat(repeatedStart.path("payload").path("matchId").asText()).isEqualTo(matchId);

            guestSocket.send("SYNC_REQUEST", "sync-before-close", "{\"gameMode\":\"ARAM\"}");
            JsonNode beforeClose = guestSocket.await(event("GAME_STATE", "sync-before-close"));
            assertThat(beforeClose.path("payload").path("moveCount").asInt()).isEqualTo(3);
            assertThat(beforeClose.path("payload").path("fen"))
                    .isEqualTo(whiteSecond.path("payload").path("fen"));

            guestSocket.close();
            guestSocket = connect(guest.token(), roomCode);
            guestSocket.send("SYNC_REQUEST", "sync-after-reconnect", "{\"gameMode\":\"ARAM\"}");
            JsonNode afterReconnect = guestSocket.await(event("GAME_STATE", "sync-after-reconnect"));
            assertThat(afterReconnect.path("payload").path("matchId").asText()).isEqualTo(matchId);
            assertThat(afterReconnect.path("payload").path("moveCount").asInt()).isEqualTo(3);
            assertThat(afterReconnect.path("payload").path("aramState"))
                    .isEqualTo(beforeClose.path("payload").path("aramState"));
        } finally {
            if (hostReplica != null) {
                hostReplica.close();
            }
            hostSocket.close();
            guestSocket.close();
        }
    }

    private Session signup(String username) throws Exception {
        JsonNode response = post("/api/auth/signup", null,
                "{\"username\":\"" + username + "\",\"password\":\"123456\"}");
        assertThat(response.path("code").asInt()).isEqualTo(1000);
        return new Session(username, response.path("result").path("token").asText());
    }

    private JsonNode post(String path, String token, String body) throws Exception {
        HttpResponse<String> response = postRaw(path, token, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return jsonMapper.readTree(response.body());
    }

    private HttpResponse<String> postRaw(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private SocketProbe connect(String token, String roomCode) throws Exception {
        URI uri = URI.create("ws://127.0.0.1:" + port + "/ws/chess?token=" + encode(token)
                + "&roomCode=" + encode(roomCode));
        SocketProbe probe = new SocketProbe(jsonMapper);
        probe.socket = http.newWebSocketBuilder().connectTimeout(TIMEOUT)
                .buildAsync(uri, probe).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        return probe;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Predicate<JsonNode> event(String type, String requestId) {
        return node -> type.equals(node.path("type").asText())
                && (requestId == null || requestId.equals(node.path("requestId").asText()));
    }

    private void assertSameAramStart(JsonNode left, JsonNode right) {
        JsonNode leftPayload = left.path("payload");
        JsonNode rightPayload = right.path("payload");
        assertThat(leftPayload.path("gameMode").asText()).isEqualTo("ARAM");
        assertThat(leftPayload.path("matchId")).isEqualTo(rightPayload.path("matchId"));
        assertThat(leftPayload.path("aramSeed")).isEqualTo(rightPayload.path("aramSeed"));
        assertThat(leftPayload.path("aramState")).isEqualTo(rightPayload.path("aramState"));
        assertThat(leftPayload.path("aramState").path("white").path("buff").asText()).isNotBlank();
        assertThat(leftPayload.path("aramState").path("black").path("buff").asText()).isNotBlank();
    }

    private record Session(String username, String token) {
    }

    private static final class SocketProbe implements WebSocket.Listener {
        private final JsonMapper jsonMapper;
        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final StringBuilder fragments = new StringBuilder();
        private volatile WebSocket socket;
        private volatile Throwable failure;

        private SocketProbe(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (fragments) {
                fragments.append(data);
                if (last) {
                    try {
                        messages.offer(jsonMapper.readTree(fragments.toString()));
                    } catch (Exception exception) {
                        failure = exception;
                    } finally {
                        fragments.setLength(0);
                    }
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            failure = error;
        }

        private void send(String type, String requestId, String payload) throws Exception {
            sendAsync(type, requestId, payload).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        private java.util.concurrent.CompletableFuture<WebSocket> sendAsync(
                String type, String requestId, String payload) {
            String message = "{\"type\":\"" + type + "\",\"requestId\":\"" + requestId
                    + "\",\"payload\":" + payload + "}";
            return socket.sendText(message, true);
        }

        private JsonNode await(Predicate<JsonNode> predicate) throws Exception {
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                if (failure != null) {
                    throw new AssertionError("WebSocket listener failed", failure);
                }
                JsonNode message = messages.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && predicate.test(message)) {
                    return message;
                }
            }
            throw new AssertionError("Timed out waiting for WebSocket event; queued=" + messages);
        }

        private void close() {
            WebSocket current = socket;
            if (current != null && !current.isOutputClosed()) {
                try {
                    current.sendClose(WebSocket.NORMAL_CLOSURE, "test complete")
                            .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    current.abort();
                }
            }
        }
    }
}
