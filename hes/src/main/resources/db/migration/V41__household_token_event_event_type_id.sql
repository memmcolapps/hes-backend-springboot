-- Link household token event rows to event_type (same model as event_log).

ALTER TABLE household_recharge_token_event
    ADD COLUMN event_type_id INT REFERENCES event_type(id);

ALTER TABLE household_management_token_event
    ADD COLUMN event_type_id INT REFERENCES event_type(id);

UPDATE household_recharge_token_event h
SET event_type_id = et.id
FROM event_type et
WHERE et.obis_code = h.profile_obis
  AND h.event_type_id IS NULL;

UPDATE household_recharge_token_event
SET event_type_id = 5
WHERE event_type_id IS NULL;

UPDATE household_management_token_event h
SET event_type_id = et.id
FROM event_type et
WHERE et.obis_code = h.profile_obis
  AND h.event_type_id IS NULL;

UPDATE household_management_token_event
SET event_type_id = 5
WHERE event_type_id IS NULL;

ALTER TABLE household_recharge_token_event
    ALTER COLUMN event_type_id SET NOT NULL;

ALTER TABLE household_management_token_event
    ALTER COLUMN event_type_id SET NOT NULL;
