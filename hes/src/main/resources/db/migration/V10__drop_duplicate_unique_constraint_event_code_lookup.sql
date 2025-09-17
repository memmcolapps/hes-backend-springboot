-- ✅ V24 – Drop duplicate unique constraint on event_code_lookup

ALTER TABLE event_code_lookup
    DROP CONSTRAINT IF EXISTS uk709bei7h1wpnfs4nqqnehn5t5;