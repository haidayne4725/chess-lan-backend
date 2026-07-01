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
export DB_PASSWORD=root
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

## Deploy To Render

This repo is ready for Render with:

- `server.port=${PORT:8080}`
- `render.yaml` at the project root
- support for Render-style `DATABASE_URL` values such as `postgres://...` or `postgresql://...`

### Option 1: Use `render.yaml`

1. Push this project to GitHub.
2. In Render, create a PostgreSQL database named `chess-lan-postgres`.
3. Create a new Blueprint or Web Service from the repository.
4. Render will pick up `render.yaml` automatically.

### Option 2: Manual Web Service Setup

- Build Command:

```bash
./mvnw clean package
```

- Start Command:

```bash
java -jar target/chess-lan-backend-0.0.1-SNAPSHOT.jar
```

### Required Environment Variables

- `JWT_SECRET`
- `DATABASE_URL`

The application can read either:

- `DATABASE_URL` / `POSTGRES_URL` in Render URL format
- or the explicit JDBC-style variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

Example external JDBC setup:

```bash
DB_URL=jdbc:postgresql://host:5432/chess_game
DB_USERNAME=postgres
DB_PASSWORD=secret
JWT_SECRET=your-base64url-secret-at-least-32-bytes
```

### Notes For Render

- Health check path: `/v3/api-docs`
- Swagger after deploy: `https://YOUR-SERVICE.onrender.com/swagger-ui.html`
- WebSocket endpoint:

```text
wss://YOUR-SERVICE.onrender.com/ws/chess?roomCode=ABC123&token=ACCESS_TOKEN
```

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
