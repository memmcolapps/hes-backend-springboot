-- Migration V5: Alter event_type_id columns to BIGINT
ALTER TABLE event_log
    ALTER COLUMN event_type_id TYPE BIGINT;