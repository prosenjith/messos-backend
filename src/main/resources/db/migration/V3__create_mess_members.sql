CREATE TABLE IF NOT EXISTS mess_members (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id   UUID        NOT NULL REFERENCES messes(id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role      VARCHAR(10) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (mess_id, user_id)
);
