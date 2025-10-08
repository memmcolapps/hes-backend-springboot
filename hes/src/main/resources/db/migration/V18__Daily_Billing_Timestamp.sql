create table public.daily_billing_profile_new
(
    meter_serial             varchar(50) not null,
    meter_model              varchar(50) not null,
    entry_timestamp          timestamp(0)   not null,
    received_at              timestamp default CURRENT_TIMESTAMP,
    total_active_energy      double precision,
    t1_active_energy         double precision,
    t2_active_energy         double precision,
    t3_active_energy         double precision,
    t4_active_energy         double precision,
    total_apparent_energy    double precision,
    t1_total_apparent_energy double precision,
    t3_total_apparent_energy double precision,
    t4_total_apparent_energy double precision,
    constraint pk_daily_billing_new
        primary key (meter_serial, entry_timestamp)
)
    partition by RANGE (entry_timestamp);

create table public.daily_billing_profile_202505_new
    partition of public.daily_billing_profile_new
        FOR VALUES FROM ('2025-05-01 00:00:00') TO ('2025-06-01 00:00:00');

create table public.daily_billing_profile_202506_new
    partition of public.daily_billing_profile_new
        FOR VALUES FROM ('2025-06-01 00:00:00') TO ('2025-07-01 00:00:00');

create table public.daily_billing_profile_202507_new
    partition of public.daily_billing_profile_new
        FOR VALUES FROM ('2025-07-01 00:00:00') TO ('2025-08-01 00:00:00');

create table public.daily_billing_profile_202508_new
    partition of public.daily_billing_profile_new
        FOR VALUES FROM ('2025-08-01 00:00:00') TO ('2025-09-01 00:00:00');

create table public.daily_billing_profile_202509_new
    partition of public.daily_billing_profile_new
        FOR VALUES FROM ('2025-09-01 00:00:00') TO ('2025-10-01 00:00:00');

create table public.daily_billing_profile_202510_new
    partition of public.daily_billing_profile_new
        FOR VALUES FROM ('2025-10-01 00:00:00') TO ('2025-11-01 00:00:00');

--Insert into new tables
INSERT INTO public.daily_billing_profile_new
select meter_serial, meter_model, entry_timestamp, received_at, total_active_energy,
       t1_active_energy, t2_active_energy, t3_active_energy, t4_active_energy,
       total_apparent_energy, t1_total_apparent_energy, t3_total_apparent_energy,
       t4_total_apparent_energy
from public.daily_billing_profile;

-- 4. Swap tables
-- Once you confirm data integrity:
DROP TABLE public.daily_billing_profile CASCADE;

ALTER TABLE public.daily_billing_profile_new
    RENAME TO daily_billing_profile;

ALTER INDEX pk_daily_billing_new
    RENAME TO pk_daily_billing;


-- 🔄 Rename partitions to remove the "_new" suffix
ALTER TABLE public.daily_billing_profile_202505_new RENAME TO daily_billing_profile_202505;
ALTER TABLE public.daily_billing_profile_202506_new RENAME TO daily_billing_profile_202506;
ALTER TABLE public.daily_billing_profile_202507_new RENAME TO daily_billing_profile_202507;
ALTER TABLE public.daily_billing_profile_202508_new RENAME TO daily_billing_profile_202508;
ALTER TABLE public.daily_billing_profile_202509_new RENAME TO daily_billing_profile_202509;
ALTER TABLE public.daily_billing_profile_202510_new RENAME TO daily_billing_profile_202510;

