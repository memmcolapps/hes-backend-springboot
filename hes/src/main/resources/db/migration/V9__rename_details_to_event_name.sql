-- ✅ V23 – Rename details → event_name in event_log

ALTER TABLE event_log
    RENAME COLUMN details TO event_name;