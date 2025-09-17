-- Change data type of current_threshold from varchar(10) to double precision
-- 🔎 USING NULLIF(current_threshold, '')::double precision ensures that if
-- there are any empty strings in the old column, they are converted to NULL instead of causing a cast error.

ALTER TABLE public.event_log
    ALTER COLUMN current_threshold TYPE double precision
        USING NULLIF(current_threshold, '')::double precision;
