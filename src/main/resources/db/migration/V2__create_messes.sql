CREATE TABLE IF NOT EXISTS messes (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    join_code  VARCHAR(8)   NOT NULL UNIQUE,
    manager_id UUID         NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
