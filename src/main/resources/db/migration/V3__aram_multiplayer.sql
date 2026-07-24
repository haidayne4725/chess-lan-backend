ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS game_mode varchar(20) NOT NULL DEFAULT 'CLASSIC';

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS game_mode varchar(20) NOT NULL DEFAULT 'CLASSIC',
    ADD COLUMN IF NOT EXISTS aram_seed varchar(80),
    ADD COLUMN IF NOT EXISTS aram_state text;

CREATE INDEX IF NOT EXISTS idx_rooms_game_mode
    ON rooms (game_mode);

CREATE INDEX IF NOT EXISTS idx_matches_game_mode
    ON matches (game_mode);
