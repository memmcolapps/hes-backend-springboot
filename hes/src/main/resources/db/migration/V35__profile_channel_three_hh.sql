CREATE TABLE IF NOT EXISTS public.profile_channel_three_hh
(
    meter_serial character varying(50) COLLATE pg_catalog."default" NOT NULL,
    model_number character varying(50) COLLATE pg_catalog."default" NOT NULL,
    entry_timestamp timestamp(0) without time zone NOT NULL,
    active_power_l1 double precision,
    active_power_l2 double precision,
    active_power_l3 double precision,
    power_factor_l1 double precision,
    power_factor_l2 double precision,
    power_factor_l3 double precision,
    grid_frequency double precision,
    volt_angle_l1_l2 double precision,
    received_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_profile_channel_three_hh PRIMARY KEY (meter_serial, entry_timestamp)
) PARTITION BY RANGE (entry_timestamp);
