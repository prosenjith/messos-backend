ALTER TABLE cycle_summaries
    ADD COLUMN utility_share DECIMAL(10,2) NOT NULL DEFAULT 0;

ALTER TABLE monthly_cycles
    ADD COLUMN total_expenses        DECIMAL(10,2),
    ADD COLUMN total_utility_expense DECIMAL(10,2),
    ADD COLUMN total_meals           DECIMAL(8,2);
