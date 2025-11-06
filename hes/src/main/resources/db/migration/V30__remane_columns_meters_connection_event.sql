DO
$$
    BEGIN
        -- 🧩 rename connection_time column if exists
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'meters_connection_event'
              AND column_name = 'connection_time'
        ) THEN
            ALTER TABLE public.meters_connection_event RENAME COLUMN connection_time TO online_time;
            RAISE NOTICE 'rename column: connection_time';
        ELSE
            RAISE NOTICE 'Column connection_time does not exist, skipping...';
        END IF;

        -- 🧩 rename updated_at column if exists
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'meters_connection_event'
              AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE public.meters_connection_event RENAME COLUMN updated_at TO offline_time;
            RAISE NOTICE 'rename column: updated_at';
        ELSE
            RAISE NOTICE 'Column updated_at does not exist, skipping...';
        END IF;
    END
$$;