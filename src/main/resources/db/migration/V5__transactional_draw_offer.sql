-- PostgreSQL, idempotent; apply explicitly if Hibernate schema update is disabled.
-- Pending offers from older processes were in memory and cannot be backfilled.
ALTER TABLE matches ADD COLUMN IF NOT EXISTS draw_offer_player_id uuid;
