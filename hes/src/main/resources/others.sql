select * from model_profile_metadata
where profile_obis = '1.0.99.1.0.255'
  and class_id = 4;

SELECT DISTINCT profile_obis from model_profile_metadata;

--1.0.99.2.0.255 - Profile channel 2
--1.0.99.1.0.255 - Profile channel 1
--0.0.98.2.0.255 - Daily billing channel 1
--0.0.98.1.0.255 - Monthly billing channel 1
--0.0.99.98.0.255 - Standard Event Log
--0.0.99.98.1.255 - Fraud Event Logs
--0.0.99.98.2.255 - Control Event Logs
--0.0.99.98.4.255 - Power Grid Event Logs
-- delete from model_profile_metadata
-- where profile_obis = '1.0.99.1.0.255';

select * from model_profile_metadata;
select * from model_profile_metadata_scalers;
select * from model_profile_metadata_captured_obis;
select * from dlms_obis_objects;  --Association view
select * from profile_reading_energy;
select * from obis_mapping; --Has OBIS Combined
select * from meter_profile_state; --Stored last read timestamp
-- delete from obis_mapping;

ALTER TABLE public.obis_mapping
    ALTER COLUMN obis_code_combined TYPE varchar(50),
    ALTER COLUMN obis_code TYPE varchar(50);
