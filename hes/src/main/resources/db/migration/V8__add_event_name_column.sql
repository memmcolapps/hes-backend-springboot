-- Modify V2__add_event_name_column.sql to include a unique constraint:
-- This ensures (event_type_id, code) is always unique.

ALTER TABLE public.event_code_lookup
    ADD COLUMN event_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE public.event_code_lookup
    ADD CONSTRAINT uq_event_type_code UNIQUE (event_type_id, code);

