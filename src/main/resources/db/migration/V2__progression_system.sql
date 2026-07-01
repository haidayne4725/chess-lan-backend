ALTER TABLE users
    ADD COLUMN IF NOT EXISTS level integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS exp bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gold bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_matches bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_wins bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_losses bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_draws bigint NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS level_requirements (
    id uuid PRIMARY KEY,
    created_at timestamp,
    updated_at timestamp,
    level integer NOT NULL UNIQUE,
    required_exp bigint NOT NULL
);

INSERT INTO level_requirements (id, created_at, updated_at, level, required_exp)
SELECT CAST(seed.id AS uuid), now(), now(), seed.level, seed.required_exp
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000001', 1, 0),
        ('00000000-0000-0000-0000-000000000002', 2, 100),
        ('00000000-0000-0000-0000-000000000003', 3, 250),
        ('00000000-0000-0000-0000-000000000004', 4, 500),
        ('00000000-0000-0000-0000-000000000005', 5, 800),
        ('00000000-0000-0000-0000-000000000006', 6, 1200),
        ('00000000-0000-0000-0000-000000000007', 7, 1700),
        ('00000000-0000-0000-0000-000000000008', 8, 2300),
        ('00000000-0000-0000-0000-000000000009', 9, 3000),
        ('00000000-0000-0000-0000-000000000010', 10, 3800)
) AS seed(id, level, required_exp)
WHERE NOT EXISTS (
    SELECT 1
    FROM level_requirements existing
    WHERE existing.level = seed.level
);

CREATE TABLE IF NOT EXISTS reward_logs (
    id uuid PRIMARY KEY,
    created_at timestamp,
    updated_at timestamp,
    user_id uuid NOT NULL,
    match_id uuid NOT NULL,
    reward_type varchar(20) NOT NULL,
    amount bigint NOT NULL,
    description varchar(255) NOT NULL,
    CONSTRAINT uk_reward_logs_user_match_type_desc
        UNIQUE (user_id, match_id, reward_type, description)
);

CREATE INDEX IF NOT EXISTS idx_reward_logs_user_id_created_at
    ON reward_logs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reward_logs_match_id
    ON reward_logs (match_id);
