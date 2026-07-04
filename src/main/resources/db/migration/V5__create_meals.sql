CREATE TABLE IF NOT EXISTS meals (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id         UUID         NOT NULL REFERENCES messes(id) ON DELETE CASCADE,
    member_id       UUID         NOT NULL REFERENCES mess_members(id) ON DELETE CASCADE,
    date            DATE         NOT NULL,
    breakfast_count DECIMAL(3,1) NOT NULL DEFAULT 0,
    lunch_count     DECIMAL(3,1) NOT NULL DEFAULT 0,
    dinner_count    DECIMAL(3,1) NOT NULL DEFAULT 0,
    updated_by      UUID         NOT NULL REFERENCES users(id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (member_id, date)
);
