/*1. Partitioned Table Design
               We’ll partition by month on entry_timestamp, so each month’s readings are stored in their own child table.
This keeps indexes small and queries fast.
*/
-- Main parent table (no actual data stored here)
CREATE TABLE profile_channel_one (
                                     id BIGSERIAL NOT NULL,
                                     meter_serial VARCHAR(50) NOT NULL,
                                     model_number VARCHAR(50) NOT NULL,
                                     entry_timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                                     meter_health_indicator INTEGER,
                                     total_instantaneous_active_power DOUBLE PRECISION,
                                     total_instantaneous_apparent_power DOUBLE PRECISION,
                                     l1_current_harmonic_thd DOUBLE PRECISION,
                                     l2_current_harmonic_thd DOUBLE PRECISION,
                                     l3_current_harmonic_thd DOUBLE PRECISION,
                                     l1_voltage_harmonic_thd DOUBLE PRECISION,
                                     l2_voltage_harmonic_thd DOUBLE PRECISION,
                                     l3_voltage_harmonic_thd DOUBLE PRECISION,
                                     received_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT pk_profile_channel_one PRIMARY KEY (id, entry_timestamp),
                                     CONSTRAINT uq_meter_entry UNIQUE (meter_serial, entry_timestamp)
) PARTITION BY RANGE (entry_timestamp);

-- Example: Create partition for August 2025
CREATE TABLE profile_channel_one_2025_08 PARTITION OF profile_channel_one
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');

-- Example: Create partition for September 2025
CREATE TABLE profile_channel_one_2025_09 PARTITION OF profile_channel_one
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');

-- Add index to each partition for fast lookups
CREATE INDEX idx_profile_channel_one_2025_08_meter_time
    ON profile_channel_one_2025_08 (meter_serial, entry_timestamp ASC);

CREATE INDEX idx_profile_channel_one_2025_09_meter_time
    ON profile_channel_one_2025_09 (meter_serial, entry_timestamp ASC);

/*
3. Automation for New Partitions
We can write a stored procedure or cron job that creates next month’s partition automatically so ingestion never fails due to missing partitions.
Example procedure:
*/
CREATE OR REPLACE FUNCTION create_next_month_partition()
    RETURNS void AS $$
DECLARE
    next_month DATE := date_trunc('month', now()) + INTERVAL '1 month';
    following_month DATE := next_month + INTERVAL '1 month';
    partition_name TEXT := 'profile_channel_one_' || to_char(next_month, 'YYYY_MM');
BEGIN
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I PARTITION OF profile_channel_one
        FOR VALUES FROM (%L) TO (%L)',
                   partition_name, next_month, following_month
            );

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS idx_%I_meter_time
        ON %I (meter_serial, entry_timestamp DESC)',
                   partition_name, partition_name
            );
END;
$$ LANGUAGE plpgsql;

/*
Alright ✅ — here’s a PostgreSQL automation that will auto-create monthly partitions for
profile_channel_one when new data comes in.

How it works
•	When you insert a new row into profile_channel_one,
      the trigger will:
      1.	Check the month of entry_timestamp.
      2.	Create that month’s partition if it doesn’t exist.
      3.	Add an index automatically for faster queries.

Function to create partitions automatically
*/

CREATE OR REPLACE FUNCTION create_profile_channel_one_partition()
    RETURNS TRIGGER AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
    create_sql TEXT;
BEGIN
    -- Calculate month start and end based on incoming row
    start_date := date_trunc('month', NEW.entry_timestamp)::DATE;
    end_date := (start_date + INTERVAL '1 month')::DATE;

    -- Partition table name e.g., profile_channel_one_2025_08
    partition_name := format('profile_channel_one_%s', to_char(start_date, 'YYYY_MM'));

    -- Check if the partition exists
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
                 JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = partition_name
    ) THEN
        -- Build and execute dynamic SQL for creating partition
        create_sql := format($f$
            CREATE TABLE IF NOT EXISTS %I PARTITION OF profile_channel_one
            FOR VALUES FROM ('%s') TO ('%s');

            CREATE INDEX IF NOT EXISTS idx_%I_meter_time
            ON %I (meter_serial, entry_timestamp);
        $f$, partition_name, start_date, end_date, partition_name, partition_name);

        EXECUTE create_sql;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- 2️⃣ Trigger to call the function before insert

CREATE TRIGGER trg_create_partition
    BEFORE INSERT ON profile_channel_one
    FOR EACH ROW
EXECUTE FUNCTION create_profile_channel_one_partition();

-- Move existing data into new partitions
-- Run month-by-month
INSERT INTO profile_channel_one_2025_08
SELECT * FROM profile_channel_one
WHERE entry_timestamp >= '2025-08-01'
  AND entry_timestamp < '2025-09-01';

