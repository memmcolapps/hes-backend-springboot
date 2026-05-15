-- Household recharge / management token event logs (multi-column DLMS structures).
-- Not stored in event_log; no event_code_lookup resolution.

CREATE TABLE household_recharge_token_event (
    id                  BIGSERIAL PRIMARY KEY,
    meter_serial        VARCHAR(50)  NOT NULL,
    meter_model         VARCHAR(50)  NOT NULL,
    profile_obis        VARCHAR(32)  NOT NULL,
    event_code          INT          NOT NULL,
    event_time          TIMESTAMP    NOT NULL,
    recharge_amount_kwh DOUBLE PRECISION,
    recharge_token      VARCHAR(512),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_hh_recharge_token_event UNIQUE (meter_serial, event_code, event_time)
);

CREATE INDEX idx_hh_recharge_token_event_meter_time
    ON household_recharge_token_event (meter_serial, event_time DESC);

CREATE TABLE household_management_token_event (
    id                  BIGSERIAL PRIMARY KEY,
    meter_serial        VARCHAR(50)  NOT NULL,
    meter_model         VARCHAR(50)  NOT NULL,
    profile_obis        VARCHAR(32)  NOT NULL,
    event_code          INT          NOT NULL,
    event_time          TIMESTAMP    NOT NULL,
    manage_token_type   VARCHAR(128),
    manage_token        VARCHAR(512),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_hh_management_token_event UNIQUE (meter_serial, event_code, event_time)
);

CREATE INDEX idx_hh_management_token_event_meter_time
    ON household_management_token_event (meter_serial, event_time DESC);
