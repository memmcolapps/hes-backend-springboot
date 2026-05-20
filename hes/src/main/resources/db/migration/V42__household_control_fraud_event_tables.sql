-- Household control / fraud event logs (extended capture vs MD two-column event_log).

CREATE TABLE household_control_event (
    id                  BIGSERIAL PRIMARY KEY,
    meter_serial        VARCHAR(50)  NOT NULL,
    meter_model         VARCHAR(50)  NOT NULL,
    profile_obis        VARCHAR(32)  NOT NULL,
    event_type_id       INT          NOT NULL REFERENCES event_type(id),
    event_code          INT          NOT NULL,
    event_time          TIMESTAMP    NOT NULL,
    reason_of_operation VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_hh_control_event UNIQUE (meter_serial, event_code, event_time)
);

CREATE INDEX idx_hh_control_event_meter_time
    ON household_control_event (meter_serial, event_time DESC);

CREATE TABLE household_fraud_event (
    id                           BIGSERIAL PRIMARY KEY,
    meter_serial                 VARCHAR(50)  NOT NULL,
    meter_model                  VARCHAR(50)  NOT NULL,
    profile_obis                 VARCHAR(32)  NOT NULL,
    event_type_id                INT          NOT NULL REFERENCES event_type(id),
    event_code                   INT          NOT NULL,
    event_time                   TIMESTAMP    NOT NULL,
    total_absolute_active_kwh    DOUBLE PRECISION,
    balance_kwh                  DOUBLE PRECISION,
    created_at                   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_hh_fraud_event UNIQUE (meter_serial, event_code, event_time)
);

CREATE INDEX idx_hh_fraud_event_meter_time
    ON household_fraud_event (meter_serial, event_time DESC);
