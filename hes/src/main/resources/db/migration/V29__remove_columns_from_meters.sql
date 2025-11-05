DO
$$
    BEGIN
        -- 🧩 Drop asset_id column if exists
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'meters'
              AND column_name = 'asset_id'
        ) THEN
            ALTER TABLE public.meters DROP COLUMN asset_id;
            RAISE NOTICE 'Dropped column: asset_id';
        ELSE
            RAISE NOTICE 'Column asset_id does not exist, skipping...';
        END IF;
    END
$$;
