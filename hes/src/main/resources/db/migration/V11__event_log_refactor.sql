-- 1. Drop duplicate constraint
ALTER TABLE public.event_log
    DROP CONSTRAINT IF EXISTS uk75fah7riyyx5hipjthww0sq8;

-- 2. Alter event_time to timestamp(0) (removes milliseconds)
ALTER TABLE public.event_log
    ALTER COLUMN event_time TYPE timestamp(0) USING date_trunc('second', event_time);

-- 3. Rename details → current_threshold
ALTER TABLE public.event_log
    RENAME COLUMN phase TO current_threshold;