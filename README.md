# Chess LAN Backend

Spring Boot REST + raw WebSocket backend for a Unity LAN chess game.

## Architecture

- [Unified full architecture](docs/full-architecture.md)
- [Unity integration guide](docs/unity-integration.md)
- [Backend API flow](docs/api-flow.md)

## Included

- Signup and login with BCrypt passwords
- JWT access token
- Logout endpoint for clearing the Unity session
- Current-player profile
- Create and join room
- Authenticated raw WebSocket room at `/ws/chess`
- Server-authoritative legal move validation with chesslib
- Checkmate, draw agreement and resignation
- Persistent match and move history
- Standard ELO updates
- Active-match recovery endpoint
- Swagger UI
- Standard `ApiResponse` response format

## Requirements

- Java 17
- PostgreSQL
- Maven wrapper included

## Database

```sql
CREATE DATABASE chess_game;
```

Configure environment variables when the defaults do not match your machine:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/chess_game
export DB_USERNAME=postgres
export DB_PASSWORD=123456
export JWT_SECRET=your-base64url-secret-at-least-32-bytes
```

## Run

```bash
./mvnw spring-boot:run
```

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
```

For another device on the same LAN, replace `localhost` with the server PC IPv4 address.

## Main REST flow

1. `POST /api/auth/signup`
2. `POST /api/auth/login`
3. Save `result.token` in Unity.
4. Send `Authorization: Bearer <token>` to protected APIs.
5. `GET /api/users/me`
6. `POST /api/auth/logout` and delete the local token.

## Room and WebSocket flow

1. Host calls `POST /api/rooms/create`.
2. Guest calls `POST /api/rooms/join`.
3. Both connect to:

```text
ws://SERVER_IP:8080/ws/chess?roomCode=ABC123&token=ACCESS_TOKEN
```

4. Both players send `READY`:

```json
{
  "type": "READY",
  "requestId": "ready-1",
  "payload": {}
}
```

5. The server broadcasts `GAME_START`.

6. Send moves using:

```json
{
  "type": "MOVE",
  "requestId": "move-1",
  "payload": {
    "from": "e2",
    "to": "e4",
    "promotion": null
  }
}
```

The backend validates the player, turn and legal move before persisting and broadcasting `MOVE_RESULT`.

See [docs/unity-integration.md](docs/unity-integration.md) for request bodies and Unity code.
