-- 1. Create the new flat table

CREATE TABLE public.monthly_billing_profile_new
(
    meter_serial                 varchar(50) not null,
    meter_model                  varchar(50) not null,
    entry_timestamp              timestamp(0) not null,
    received_at                  timestamp default CURRENT_TIMESTAMP,
    total_absolute_active_energy double precision,
    export_active_energy         double precision,
    import_active_energy         double precision,
    import_reactive_energy       double precision,
    export_reactive_energy       double precision,
    remaining_credit_amount      double precision,
    import_active_md             double precision,
    import_active_md_time        timestamp,
    t1_active_energy             double precision,
    t2_active_energy             double precision,
    t3_active_energy             double precision,
    t4_active_energy             double precision,
    total_active_energy          double precision,
    total_apparent_energy        double precision,
    t1_total_apparent_energy     double precision,
    t2_total_apparent_energy     double precision,
    t3_total_apparent_energy     double precision,
    t4_total_apparent_energy     double precision,
    active_maximum_demand        double precision,
    total_apparent_demand        double precision,
    total_apparent_demand_time   timestamp,
    CONSTRAINT pk_monthly_billing_new
        PRIMARY KEY (meter_serial, entry_timestamp)
);
ALTER TABLE public.monthly_billing_profile_new OWNER TO postgres;


/*2. Migrate data from partitions
Since the existing table is partitioned, the parent table (monthly_billing_profile) is enough — it queries across all partitions.
So you can insert everything at once:*/

INSERT INTO public.monthly_billing_profile_new
SELECT
    meter_serial,
    meter_model,
    entry_timestamp::timestamp(0),
    received_at,
    total_absolute_active_energy,
    export_active_energy,
    import_active_energy,
    import_reactive_energy,
    export_reactive_energy,
    remaining_credit_amount,
    import_active_md,
    import_active_md_time,
    t1_active_energy,
    t2_active_energy,
    t3_active_energy,
    t4_active_energy,
    total_active_energy,
    total_apparent_energy,
    t1_total_apparent_energy,
    t2_total_apparent_energy,
    t3_total_apparent_energy,
    t4_total_apparent_energy,
    active_maximum_demand,
    total_apparent_demand,
    total_apparent_demand_time
FROM public.monthly_billing_profile;

-- 3. Swap tables

DROP TABLE public.monthly_billing_profile CASCADE;

ALTER TABLE public.monthly_billing_profile_new
    RENAME TO monthly_billing_profile;

ALTER INDEX pk_monthly_billing_new
    RENAME TO pk_monthly_billing;
