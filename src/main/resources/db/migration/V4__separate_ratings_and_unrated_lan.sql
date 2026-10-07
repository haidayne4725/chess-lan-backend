-- PostgreSQL, idempotent. Apply explicitly when Hibernate schema update is disabled.
-- This project does not currently run Flyway automatically.
-- Old users.elo is retained unchanged as historical mixed-mode Elo.
ALTER TABLE users ADD COLUMN IF NOT EXISTS classic_elo integer NOT NULL DEFAULT 1500;
ALTER TABLE users ADD COLUMN IF NOT EXISTS aram_rating double precision NOT NULL DEFAULT 1500;
ALTER TABLE users ADD COLUMN IF NOT EXISTS aram_deviation double precision NOT NULL DEFAULT 200;
ALTER TABLE users ADD COLUMN IF NOT EXISTS aram_volatility double precision NOT NULL DEFAULT 0.06;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS rated boolean NOT NULL DEFAULT false;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS statistics_processed boolean NOT NULL DEFAULT false;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS stats_applied boolean NOT NULL DEFAULT false;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS settlement_decision varchar(40) NOT NULL DEFAULT 'LEGACY';
-- Already settled legacy matches must never receive new stats on a retry.
UPDATE matches SET statistics_processed = true WHERE status <> 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_match_move_participant ON match_moves(match_id, player_id);
