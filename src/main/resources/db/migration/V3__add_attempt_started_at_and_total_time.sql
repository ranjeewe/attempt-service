ALTER TABLE attempt.attempt
    ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE attempt.attempt
    ADD COLUMN total_time_seconds INTEGER;
