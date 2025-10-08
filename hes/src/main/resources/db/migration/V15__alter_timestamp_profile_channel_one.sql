-- 1. Create a new parent table

CREATE TABLE public.profile_channel_one_new
(
    meter_serial                       varchar(50) not null,
    model_number                       varchar(50) not null,
    entry_timestamp                    timestamp(0) not null,
    meter_health_indicator             integer,
    total_instantaneous_active_power   double precision,
    total_instantaneous_apparent_power double precision,
    l1_current_harmonic_thd            double precision,
    l2_current_harmonic_thd            double precision,
    l3_current_harmonic_thd            double precision,
    l1_voltage_harmonic_thd            double precision,
    l2_voltage_harmonic_thd            double precision,
    l3_voltage_harmonic_thd            double precision,
    received_at                        timestamp default CURRENT_TIMESTAMP,
    constraint pk_profile_channel_one_new
        primary key (meter_serial, entry_timestamp)
)
    PARTITION BY RANGE (entry_timestamp);

-- 2. Recreate the partitions
-- For each month (202412 → 202508), recreate them under the new parent:

CREATE TABLE public.profile_channel_one_202412_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2024-12-01 00:00:00') TO ('2025-01-01 00:00:00');

CREATE TABLE public.profile_channel_one_202501_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-01-01 00:00:00') TO ('2025-02-01 00:00:00');

-- February 2025
CREATE TABLE public.profile_channel_one_202502_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-02-01 00:00:00') TO ('2025-03-01 00:00:00');

-- March 2025
CREATE TABLE public.profile_channel_one_202503_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-03-01 00:00:00') TO ('2025-04-01 00:00:00');

-- April 2025
CREATE TABLE public.profile_channel_one_202504_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-04-01 00:00:00') TO ('2025-05-01 00:00:00');

-- May 2025
CREATE TABLE public.profile_channel_one_202505_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-05-01 00:00:00') TO ('2025-06-01 00:00:00');

-- June 2025
CREATE TABLE public.profile_channel_one_202506_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-06-01 00:00:00') TO ('2025-07-01 00:00:00');

-- July 2025
CREATE TABLE public.profile_channel_one_202507_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-07-01 00:00:00') TO ('2025-08-01 00:00:00');

-- August 2025
CREATE TABLE public.profile_channel_one_202508_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-08-01 00:00:00') TO ('2025-09-01 00:00:00');

-- September 2025
CREATE TABLE public.profile_channel_one_202509_new
    PARTITION OF public.profile_channel_one_new
        FOR VALUES FROM ('2025-09-01 00:00:00') TO ('2025-10-01 00:00:00');


-- Repeat for 202502 → 202508

-- 3. Copy data from old to new
--     Make sure to cast/convert entry_timestamp to timestamp(0) while copying:

INSERT INTO public.profile_channel_one_new
SELECT
    meter_serial,
    model_number,
    entry_timestamp::timestamp(0),
    meter_health_indicator,
    total_instantaneous_active_power,
    total_instantaneous_apparent_power,
    l1_current_harmonic_thd,
    l2_current_harmonic_thd,
    l3_current_harmonic_thd,
    l1_voltage_harmonic_thd,
    l2_voltage_harmonic_thd,
    l3_voltage_harmonic_thd,
    received_at
FROM public.profile_channel_one;

-- This automatically routes rows into the right new partitions.

-- 4. Swap tables
-- Once you confirm data integrity:

DROP TABLE public.profile_channel_one CASCADE;

ALTER TABLE public.profile_channel_one_new
    RENAME TO profile_channel_one;

ALTER INDEX pk_profile_channel_one_new
    RENAME TO pk_profile_channel_one;

