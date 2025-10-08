create table profile_channel_two
(
    meter_serial                       varchar(50)  not null,
    model_number                       varchar(50)  not null,
    entry_timestamp                    timestamp(0) not null,
    total_import_active_energy         double precision,
    total_export_active_energy         double precision,
    received_at                        timestamp default CURRENT_TIMESTAMP,
    constraint pk_profile_channel_two
        primary key (meter_serial, entry_timestamp)
)
    partition by RANGE (entry_timestamp);

create table public.profile_channel_two_202412
    partition of public.profile_channel_two
        FOR VALUES FROM ('2024-12-01 00:00:00') TO ('2025-01-01 00:00:00');