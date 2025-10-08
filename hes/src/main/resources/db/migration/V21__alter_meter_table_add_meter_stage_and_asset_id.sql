-- Sync meters table with production changes

-- 1️⃣ Change 'status' column from BOOLEAN to VARCHAR
ALTER TABLE public.meters
    ALTER COLUMN status TYPE varchar USING status::varchar;

-- 2️⃣ Add 'meter_stage' column if not exists
ALTER TABLE public.meters
    ADD COLUMN IF NOT EXISTS meter_stage varchar;

-- 3️⃣ Add 'asset_id' column if not exists
ALTER TABLE public.meters
    ADD COLUMN IF NOT EXISTS asset_id varchar;