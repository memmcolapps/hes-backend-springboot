-- Model-scoped event code lookup and household event description columns.

ALTER TABLE event_code_lookup
    ADD COLUMN IF NOT EXISTS meter_model VARCHAR(255);

ALTER TABLE household_control_event
    ADD COLUMN IF NOT EXISTS event_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reason_description VARCHAR(128);

ALTER TABLE household_fraud_event
    ADD COLUMN IF NOT EXISTS event_name VARCHAR(255);

ALTER TABLE household_management_token_event
    ADD COLUMN IF NOT EXISTS mgt_token_type_description VARCHAR(128);
