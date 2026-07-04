CREATE TABLE IF NOT EXISTS notices (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id    UUID        NOT NULL REFERENCES messes(id) ON DELETE CASCADE,
    message    VARCHAR(500) NOT NULL,
    posted_by  UUID        NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
