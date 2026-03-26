-- Ensure profile_channel_one_v1 (backup table) has expected columns.
-- This is safe to run multiple times due to IF NOT EXISTS.

ALTER TABLE public.profile_channel_one_v1
    ADD COLUMN IF NOT EXISTS total_instantaneous_active_power   double precision,
    ADD COLUMN IF NOT EXISTS total_instantaneous_apparent_power double precision,
    ADD COLUMN IF NOT EXISTS l1_current_harmonic_thd            double precision,
    ADD COLUMN IF NOT EXISTS l2_current_harmonic_thd            double precision,
    ADD COLUMN IF NOT EXISTS l3_current_harmonic_thd            double precision,
    ADD COLUMN IF NOT EXISTS l1_voltage_harmonic_thd            double precision,
    ADD COLUMN IF NOT EXISTS l2_voltage_harmonic_thd            double precision,
    ADD COLUMN IF NOT EXISTS l3_voltage_harmonic_thd            double precision;

