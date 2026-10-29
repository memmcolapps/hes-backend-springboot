-- Bulk Job Execution and Per-Meter Audit Logging Tables
CREATE TABLE bulk_job_execution (
    id                  BIGSERIAL PRIMARY KEY,
    job_name            VARCHAR(255)  NOT NULL,
    job_group           VARCHAR(255)  NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    total_meters        INT           NOT NULL DEFAULT 0,
    interval_type       VARCHAR(20)   NULL,
    start_date          TIMESTAMP     NULL,
    end_date            TIMESTAMP     NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at          TIMESTAMP     NULL,
    completed_at        TIMESTAMP     NULL,
    error_message       TEXT          NULL
);

CREATE TABLE bulk_job_meter_log (
    id                  BIGSERIAL PRIMARY KEY,
    job_execution_id    BIGINT        NOT NULL REFERENCES bulk_job_execution(id) ON DELETE CASCADE,
    meter_number        VARCHAR(100)  NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempt_count       INT           NOT NULL DEFAULT 0,
    last_error          TEXT          NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP     NULL,
    duration_ms         BIGINT        NULL
);

CREATE INDEX idx_bulk_job_meter_log_job_id ON bulk_job_meter_log (job_execution_id);
CREATE INDEX idx_bulk_job_meter_log_status ON bulk_job_meter_log (job_execution_id, status);
CREATE INDEX idx_bulk_job_execution_status ON bulk_job_execution (status);
