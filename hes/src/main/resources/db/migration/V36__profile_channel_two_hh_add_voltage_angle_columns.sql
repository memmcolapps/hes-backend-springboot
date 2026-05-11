ALTER TABLE public.profile_channel_two_hh
    ADD COLUMN IF NOT EXISTS volt_angle_l1_l2 double precision,
    ADD COLUMN IF NOT EXISTS volt_angle_l1_l3 double precision;
