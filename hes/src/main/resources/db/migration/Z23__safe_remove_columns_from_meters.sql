-- Purpose: Safely remove activate_status, energy_type, and meter_model columns from meters table (if they exist)

DO
$$
    BEGIN
        -- 🧩 Drop activate_status column if exists
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'meters'
              AND column_name = 'activate_status'
        ) THEN
            ALTER TABLE public.meters DROP COLUMN activate_status;
            RAISE NOTICE 'Dropped column: activate_status';
        ELSE
            RAISE NOTICE 'Column activate_status does not exist, skipping...';
        END IF;

        -- 🧩 Drop energy_type column if exists
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'meters'
              AND column_name = 'energy_type'
        ) THEN
            ALTER TABLE public.meters DROP COLUMN energy_type;
            RAISE NOTICE 'Dropped column: energy_type';
        ELSE
            RAISE NOTICE 'Column energy_type does not exist, skipping...';
        END IF;

        -- 🧩 Drop meter_model column if exists
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'meters'
              AND column_name = 'meter_model'
        ) THEN
            ALTER TABLE public.meters DROP COLUMN meter_model;
            RAISE NOTICE 'Dropped column: meter_model';
        ELSE
            RAISE NOTICE 'Column meter_model does not exist, skipping...';
        END IF;
    END
$$;