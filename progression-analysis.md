# Progression Analysis

## Current Entities

### UserEntity
- File: `src/main/java/com/chesslan/game/model/entity/UserEntity.java`
- Current fields:
  - `id`, `createdAt`, `updatedAt` from `BaseEntity`
  - `username`
  - `password`
  - `elo`
- Notes:
  - Implements `UserDetails`
  - No progression or currency fields yet

### MatchEntity
- File: `src/main/java/com/chesslan/game/model/entity/MatchEntity.java`
- Current fields:
  - room linkage
  - white/black players
  - winner
  - status
  - terminationReason
  - currentFen
  - moveCount
  - white/black ELO before/after
  - startedAt
  - finishedAt
  - optimistic `version`
- Notes:
  - Already contains enough match-end metadata to derive reward rules
  - `terminationReason` can identify `CHECKMATE`
  - `moveCount` can support the `> 30 moves` bonus

### MatchMoveEntity
- Stores persisted move history by match and request id
- Important for idempotent multiplayer flow

### RoomEntity
- Represents host/guest room lifecycle
- Match completion already flips room status to `FINISHED`

## Current Services

### AuthServiceImpl
- Signup creates `UserEntity`
- Initializes `elo = 1200`
- Uses `GameMapper.toUserProfile(...)` for auth response payload

### UserServiceImpl
- Exposes `me(username)`
- Loads user by username and maps to `UserProfileResponseDTO`

### MatchServiceImpl
- Owns match lifecycle:
  - `startMatch`
  - `submitMove`
  - `resign`
  - `offerDraw`
  - `acceptDraw`
  - `syncState`
  - `history`
  - `findById`
  - `active`
- Critical integration point:
  - `finish(...)` currently updates ELO, winner, status, room status, and persistence
- Existing anti-duplication behavior:
  - move submission uses `requestId`
  - finished matches short-circuit repeated completion

### EloCalculator
- Pure service used from `MatchServiceImpl.finish(...)`
- Must remain untouched in behavior

## Current Controllers and APIs

### AuthController
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/logout`

### UserController
- `GET /api/users/me`
- Current response fields:
  - `id`
  - `username`
  - `elo`
  - `createdAt`

### RoomController
- `POST /api/rooms/create`
- `POST /api/rooms/join`
- `GET /api/rooms/{roomCode}`

### MatchController
- `GET /api/matches/history`
- `GET /api/matches/{matchId}`
- `GET /api/matches/active`

## Persistence and Schema State

- JPA is using `spring.jpa.hibernate.ddl-auto=update` in main runtime
- Tests use H2 with `ddl-auto=create-drop`
- No Flyway or Liquibase integration exists yet
- A SQL migration file can still be added as a manual/forward-looking artifact

## Affected Components

### Directly affected
- `UserEntity`
- `GameMapper`
- `UserProfileResponseDTO`
- `UserService`
- `UserServiceImpl`
- `UserController`
- `UserControllerApi`
- `MatchServiceImpl`
- `AuthServiceImpl` signup defaults

### New components required
- `LevelRequirement` entity + repository
- `RewardLog` entity + repository
- `RewardType` enum
- `RewardService` interface + implementation
- DTOs for progression, currency, reward history
- SQL migration artifact `V2__progression_system.sql`

### Tests to extend
- `AuthFlowIntegrationTest`
- `MatchFlowIntegrationTest`
- New progression-specific integration coverage

## Integration Points

### Profile expansion
- Extend `GET /api/users/me` without breaking existing endpoint path
- Safe because response is additive

### New read APIs
- `GET /api/users/progression`
- `GET /api/users/currency`
- `GET /api/rewards/history`

### Match completion reward hook
- Best insertion point is `MatchServiceImpl.finish(...)`
- Existing order today:
  1. calculate ELO
  2. update players
  3. update match status/result
  4. persist users/room/match
- Progression should run in the same transaction after match result is known and before transaction commit

### Idempotency / duplicate reward prevention
- `finish(...)` already guards against reprocessing non-active matches
- `RewardLog` should also enforce duplicate protection per user/match/reward event

## Main Risks

- Accidentally rewarding twice if match completion is retried
- Changing `UserProfileResponseDTO` in a way that breaks auth response assertions
- Introducing reward logic that interferes with ELO persistence order
- Relying on hardcoded level thresholds instead of DB-backed requirements

## Recommended Implementation Direction

- Keep all current endpoints and service contracts
- Add progression fields to `UserEntity` with safe defaults
- Compute reward and level-up inside a transactional `RewardService`
- Call reward processing from `MatchServiceImpl.finish(...)` only after result is finalized
- Make read APIs mapper/service based, matching existing controller style
