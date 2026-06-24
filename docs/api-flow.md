# Backend Flow

## Login

```text
Unity
  -> AuthController.login
  -> AuthServiceImpl.login
  -> AuthenticationManager
  -> UserRepository
  -> BCrypt password verification
  -> JwtService
  -> ApiResponse<AuthResponseDTO>
```

## Logout

```text
Unity sends access token
  -> AuthController.logout
  -> AuthServiceImpl.logout
  -> Unity deletes the token from PlayerPrefs
```

## Create and join room

```text
Host -> RoomController.create -> RoomServiceImpl -> rooms(WAITING)
Guest -> RoomController.join -> RoomServiceImpl -> rooms(PLAYING)
```

## WebSocket

```text
Unity opens /ws/chess?roomCode=...&token=...
  -> ChessHandshakeInterceptor validates JWT and room membership
  -> ChessWebSocketHandler groups sessions by roomCode
  -> READY from both players creates Match
  -> MOVE is delegated to MatchService
  -> ChessLibRulesEngine validates the move
  -> accepted move is stored in match_moves
  -> MOVE_RESULT is broadcast to both players
  -> checkmate/draw/resign updates Match, Room and both ELO values
```

## Match history

```text
Unity -> MatchController
  -> MatchService verifies the authenticated player belongs to the match
  -> MatchRepository + MatchMoveRepository
  -> MatchResponseDTO with result, FEN, moves and ELO history
```
