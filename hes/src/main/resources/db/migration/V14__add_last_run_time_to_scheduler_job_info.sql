-- Adds last_run_time column to scheduler_job_info table

ALTER TABLE scheduler_job_info
    ADD COLUMN last_run_time TIMESTAMP NULL;