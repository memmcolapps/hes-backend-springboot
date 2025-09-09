ALTER TABLE public.event_log
    ADD CONSTRAINT uq_event_log UNIQUE (meter_serial, event_code, event_time);
