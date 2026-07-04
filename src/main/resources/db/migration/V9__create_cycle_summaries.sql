CREATE TABLE IF NOT EXISTS cycle_summaries (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id         UUID          NOT NULL REFERENCES monthly_cycles(id) ON DELETE CASCADE,
    member_id        UUID          NOT NULL REFERENCES mess_members(id)   ON DELETE CASCADE,
    member_name      VARCHAR(100)  NOT NULL,
    total_meals      DECIMAL(8,2)  NOT NULL DEFAULT 0,
    meal_cost        DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_deposited  DECIMAL(10,2) NOT NULL DEFAULT 0,
    balance          DECIMAL(10,2) NOT NULL DEFAULT 0
);
