# Progression Design

## Goal

Add EXP, level, gold, and match rewards to the existing chess backend without changing the current authentication, multiplayer, or ELO flows.

## ERD

### users
- existing columns
- new columns:
  - `level`
  - `exp`
  - `gold`
  - `total_matches`
  - `total_wins`
  - `total_losses`
  - `total_draws`

### level_requirements
- `id`
- `level` unique
- `required_exp`
- `created_at`
- `updated_at`

### reward_logs
- `id`
- `user_id`
- `match_id`
- `reward_type`
- `amount`
- `description`
- `created_at`
- `updated_at`

## Reward Flow

1. Match reaches terminal state in `MatchServiceImpl.finish(...)`.
2. Existing ELO calculation runs unchanged.
3. `rewardService.processMatchReward(match)` is called in the same transaction.
4. Service determines each player's match outcome:
   - win
   - draw
   - loss
5. Service grants base EXP and gold.
6. Service grants EXP bonuses:
   - `CHECKMATE` => `+20 EXP` to winner only
   - `moveCount > 30` => `+10 EXP` to both players
7. Service updates aggregate counters:
   - total matches
   - wins
   - losses
   - draws
8. Service runs multi-level-up evaluation against `level_requirements`.
9. `RewardLog` rows are written for audit and duplicate detection.

## Level Flow

1. User has `currentLevel` and `currentExp`.
2. After EXP grant:
   - `newExp = currentExp + rewardExp`
3. Load ordered `LevelRequirement` list from database.
4. While next level exists and `newExp >= requiredExp(nextLevel)`:
   - increment level
   - write `LEVEL_UP` log
5. Persist final level and EXP.

## Reward Rules

### Base match rewards
- Win: `100 EXP`, `150 GOLD`
- Draw: `50 EXP`, `80 GOLD`
- Loss: `30 EXP`, `50 GOLD`

### Bonus rewards
- Checkmate winner bonus: `20 EXP`
- Match longer than 30 moves: `10 EXP` to each player

## API Changes

### Extend `GET /api/users/me`
- Keep endpoint path unchanged
- Response becomes additive:
  - `level`
  - `exp`
  - `nextLevelExp`
  - `gold`
  - `totalMatches`
  - `totalWins`
  - `totalLosses`
  - `totalDraws`

### Add `GET /api/users/progression`
- Response:
  - `level`
  - `currentExp`
  - `nextLevelExp`
  - `progressPercent`

### Add `GET /api/users/currency`
- Response:
  - `gold`

### Add `GET /api/rewards/history`
- Returns reward logs for current user ordered newest first

## Affected Services

### UserService
- Add:
  - `progression(username)`
  - `currency(username)`

### RewardService
- Add:
  - `grantExp(user, match, amount, description)`
  - `grantGold(user, match, amount, description)`
  - `processLevelUp(user, match)`
  - `processMatchReward(match)`
  - `history(username)`
  - `nextLevelExp(level)`

### MatchServiceImpl
- Integrate reward processing inside `finish(...)`

## Affected Controllers

### UserController
- Add progression and currency endpoints

### RewardController
- New authenticated controller for reward history

## Transaction Plan

- `MatchServiceImpl.finish(...)` remains transactional
- `RewardServiceImpl.processMatchReward(...)` also uses `@Transactional`
- Duplicate reward defense:
  - short-circuit if reward logs already exist for the match
  - add repository query by `matchId` and `userId`
- Because reward processing stays in the same transaction as ELO update, failures roll back the entire completion flow

## Migration Plan

Add `src/main/resources/db/migration/V2__progression_system.sql` containing:
- new user columns with defaults
- create `level_requirements`
- seed initial level rows
- create `reward_logs`
- create indexes / unique constraints needed for safe lookups

Note:
- The current project does not execute Flyway migrations automatically yet.
- The SQL file is still useful as an explicit schema artifact and future Flyway adoption path.

## Compatibility Notes

- No existing endpoint paths are removed or renamed
- ELO logic stays in `EloCalculator`
- Multiplayer move submission remains unchanged
- Match history DTO stays compatible, with progression exposed through user/reward APIs only
