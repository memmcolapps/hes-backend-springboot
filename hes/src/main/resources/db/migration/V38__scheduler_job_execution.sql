-- Append-only execution audit for every Quartz trigger fire (cluster-safe observability)
CREATE TABLE scheduler_job_execution (
    id                  BIGSERIAL PRIMARY KEY,
    fire_instance_id    VARCHAR(191)  NOT NULL UNIQUE,
    job_name            VARCHAR(255)  NOT NULL,
    job_group           VARCHAR(255)  NOT NULL,
    scheduled_fire_time TIMESTAMP NULL,
    fire_time           TIMESTAMP NULL,
    started_at          TIMESTAMP     NOT NULL,
    ended_at            TIMESTAMP NULL,
    status              VARCHAR(32)   NOT NULL,
    error_message       TEXT NULL,
    scheduler_instance_id VARCHAR(191) NULL
);

CREATE INDEX idx_scheduler_job_execution_job ON scheduler_job_execution (job_name, job_group);
CREATE INDEX idx_scheduler_job_execution_started ON scheduler_job_execution (started_at DESC);
