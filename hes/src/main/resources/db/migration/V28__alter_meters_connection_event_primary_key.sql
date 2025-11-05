-- Step 1: Drop the old primary key constraint
ALTER TABLE meters_connection_event DROP CONSTRAINT IF EXISTS meters_connection_event_pkey;

-- Step 2: Drop the ID column
ALTER TABLE meters_connection_event DROP COLUMN IF EXISTS id;

-- Step 3: Set meter_no as the new Primary Key
ALTER TABLE meters_connection_event ADD PRIMARY KEY (meter_no);

-- Step 4: (Optional) Ensure meter_no is NOT NULL and has correct length
ALTER TABLE meters_connection_event
    ALTER COLUMN meter_no SET NOT NULL;

-- Step 5: Add comment (optional, for documentation)
COMMENT ON TABLE meters_connection_event IS 'Tracks meter connection events, meter_no now serves as primary key.';