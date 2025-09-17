-- Migration V6: Alter event_type.id and related FKs to BIGINT

-- 1. Drop foreign key constraints referencing event_type.id
ALTER TABLE event_code_lookup DROP CONSTRAINT IF EXISTS event_code_lookup_event_type_id_fkey;
ALTER TABLE event_log DROP CONSTRAINT IF EXISTS event_log_event_type_id_fkey;

-- 2. Drop default serial before altering event_type.id
ALTER TABLE event_type
    ALTER COLUMN id DROP DEFAULT;

-- 3. Change event_type.id type to BIGINT
ALTER TABLE event_type
    ALTER COLUMN id TYPE BIGINT;

-- 4. Recreate sequence as BIGINT (if needed)
ALTER SEQUENCE event_type_id_seq AS BIGINT;

-- 5. Reattach default
ALTER TABLE event_type
    ALTER COLUMN id SET DEFAULT nextval('event_type_id_seq');

-- 6. Alter referencing columns to BIGINT
ALTER TABLE event_code_lookup
    ALTER COLUMN event_type_id TYPE BIGINT;

ALTER TABLE event_log
    ALTER COLUMN event_type_id TYPE BIGINT;

-- 7. Recreate foreign keys
ALTER TABLE event_code_lookup
    ADD CONSTRAINT event_code_lookup_event_type_id_fkey
        FOREIGN KEY (event_type_id) REFERENCES event_type(id);

ALTER TABLE event_log
    ADD CONSTRAINT event_log_event_type_id_fkey
        FOREIGN KEY (event_type_id) REFERENCES event_type(id);