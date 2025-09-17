-- Migration V4: Alter event_type_id column to BIGINT in event_code_lookup

ALTER TABLE event_code_lookup
    ALTER COLUMN event_type_id TYPE BIGINT;