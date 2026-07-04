CREATE TABLE IF NOT EXISTS monthly_cycles (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id             UUID          NOT NULL REFERENCES messes(id) ON DELETE CASCADE,
    start_date          DATE          NOT NULL,
    end_date            DATE,
    status              VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    meal_rate_snapshot  DECIMAL(10,2),
    closed_at           TIMESTAMPTZ
);
