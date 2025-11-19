DO $$
    BEGIN
        -- Check if backup table already exists
        IF NOT EXISTS (
            SELECT FROM pg_tables
            WHERE schemaname = 'public'
              AND tablename = 'obis_mapping_backup'
        ) THEN

            -- Create backup table
            EXECUTE 'CREATE TABLE obis_mapping_backup AS
                 SELECT * FROM obis_mapping';

        END IF;
    END$$;