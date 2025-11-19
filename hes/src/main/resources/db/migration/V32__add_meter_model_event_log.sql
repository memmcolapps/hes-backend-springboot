DO
$$
    BEGIN
        -- 🧩 rename connection_time column if exists
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'event_log'
              AND column_name = 'meter_model'
        ) THEN
            ALTER TABLE public.event_log ADD COLUMN meter_model  VARCHAR(50);
            RAISE NOTICE 'meter_model Added';
        ELSE
            RAISE NOTICE 'meter_model exist, skipping...';
        END IF;
    END
$$;