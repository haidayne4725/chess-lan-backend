# Unity Integration

## 1. LAN address

Run Spring Boot on one machine and find that machine's IPv4 address.

```text
REST base URL: http://192.168.1.10:8080
WebSocket URL: ws://192.168.1.10:8080/ws/chess
```

Both Unity devices must be on the same network.

## 2. Signup

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "username": "player_one",
  "password": "123456"
}
```

Signup returns the same token structure as login.

## 3. Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "player_one",
  "password": "123456"
}
```

Important response fields:

```json
{
  "result": {
    "token": "...",
    "expiresInSeconds": 1800,
    "user": {
      "id": "...",
      "username": "player_one",
      "elo": 1200
    }
  }
}
```

Unity storage:

```csharp
PlayerPrefs.SetString("token", token);
PlayerPrefs.Save();
```

## 4. Protected request

```csharp
request.SetRequestHeader(
    "Authorization",
    "Bearer " + PlayerPrefs.GetString("token")
);
```

Profile:

```http
GET /api/users/me
Authorization: Bearer ACCESS_TOKEN
```

## 5. Logout

```http
POST /api/auth/logout
Authorization: Bearer ACCESS_TOKEN
```

After success:

```csharp
PlayerPrefs.DeleteKey("token");
PlayerPrefs.Save();
```

JWT is stateless. Logout confirms the request, then Unity removes the token locally.

## 6. Create room

```http
POST /api/rooms/create
Authorization: Bearer ACCESS_TOKEN
```

Read `result.roomCode` and show it to the host.

## 7. Join room

```http
POST /api/rooms/join
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json
```

```json
{
  "roomCode": "ABC123"
}
```

The room status changes from `WAITING` to `PLAYING`.

## 8. WebSocket

Use the NativeWebSocket Unity package.

```csharp
string serverIp = "192.168.1.10";
string roomCode = "ABC123";
string token = PlayerPrefs.GetString("token");

var socket = new WebSocket(
    $"ws://{serverIp}:8080/ws/chess?roomCode={roomCode}&token={token}"
);
```

Both players first send:

```json
{
  "type": "READY",
  "requestId": "ready-player-one",
  "payload": {}
}
```

The server sends `GAME_START` after both players are ready. The payload includes:

- `matchId`
- `whitePlayerId`
- `blackPlayerId`
- `fen`
- `turn`

Move message:

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

Do not update the board permanently until the server returns:

```json
{
  "type": "MOVE_RESULT",
  "requestId": "move-1",
  "payload": {
    "accepted": true,
    "notation": "e4",
    "fen": "...",
    "turn": "BLACK",
    "status": "ACTIVE"
  }
}
```

Supported client events:

- `READY`
- `MOVE`
- `RESIGN`
- `DRAW_OFFER`
- `DRAW_ACCEPT`
- `SYNC_REQUEST`

Supported server events:

- `PLAYER_JOINED`
- `PLAYER_DISCONNECTED`
- `PLAYER_READY`
- `GAME_START`
- `MOVE_RESULT`
- `DRAW_OFFERED`
- `GAME_STATE`
- `GAME_OVER`
- `ERROR`

## 9. Match REST APIs

```http
GET /api/matches/history
GET /api/matches/active
GET /api/matches/{matchId}
Authorization: Bearer ACCESS_TOKEN
```

History and detail responses include accepted move records, FEN after each move, result and before/after ELO.

## 10. Server-authoritative behavior

The backend now validates:

- Player belongs to the match.
- Correct turn.
- Legal chess move.
- Checkmate and chess draw rules.
- Draw agreement.
- Resignation.

Unity renders the canonical `fen` returned by the server. It does not decide the winner or modify ELO locally.
