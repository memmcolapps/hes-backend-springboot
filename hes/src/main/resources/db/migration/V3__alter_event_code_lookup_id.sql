-- Migration V3: Alter id column to BIGINT in event_code_lookup

ALTER TABLE event_code_lookup
    ALTER COLUMN id TYPE BIGINT;

-- Ensure the sequence (if any) also uses BIGINT
ALTER SEQUENCE event_code_lookup_id_seq AS BIGINT;