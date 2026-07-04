CREATE TABLE IF NOT EXISTS expenses (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id           UUID          NOT NULL REFERENCES messes(id) ON DELETE CASCADE,
    amount            DECIMAL(10,2) NOT NULL,
    date              DATE          NOT NULL,
    note              VARCHAR(200),
    receipt_photo_url VARCHAR(500),
    logged_by         UUID          NOT NULL REFERENCES users(id),
    cycle_id          UUID          NOT NULL REFERENCES monthly_cycles(id),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
