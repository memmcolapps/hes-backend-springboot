ALTER TABLE public.scheduler_job_info
    ADD COLUMN obis_codes varchar(255) DEFAULT '' NOT NULL;