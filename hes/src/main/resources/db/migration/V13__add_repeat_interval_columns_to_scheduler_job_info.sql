-- Add interval columns for scheduler_job_info
ALTER TABLE scheduler_job_info
    ADD COLUMN repeat_seconds INTEGER,
    ADD COLUMN repeat_minutes INTEGER,
    ADD COLUMN repeat_hours INTEGER;