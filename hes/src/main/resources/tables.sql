--✅ Recommended Table: model_profile_metadata

CREATE TABLE IF NOT EXISTS model_profile_metadata (
  meter_model VARCHAR NOT NULL,
  profile_obis VARCHAR NOT NULL,
  capture_obis VARCHAR NOT NULL,
  class_id INTEGER NOT NULL,
  attribute_index INTEGER NOT NULL,
  scaler DOUBLE PRECISION DEFAULT 1.0,
  unit VARCHAR,
  description VARCHAR,
  PRIMARY KEY (meter_model, profile_obis, capture_obis)
);

--Drop and add new constraint
ALTER TABLE model_profile_metadata
    DROP CONSTRAINT IF EXISTS uk_model_profile_capture;

ALTER TABLE model_profile_metadata
    ADD CONSTRAINT uk_model_profile_capture
        UNIQUE (meter_model, profile_obis, capture_obis, attribute_index);


CREATE TABLE meter_profile_state (
     id                 BIGSERIAL PRIMARY KEY,
     meter_serial       VARCHAR(20)  NOT NULL,
     profile_obis       VARCHAR(20)  NOT NULL,
     last_timestamp     TIMESTAMP WITHOUT TIME ZONE NULL,
     capture_period_sec INTEGER      NULL,
     updated_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
     CONSTRAINT ux_meter_profile_state_serial_obis UNIQUE (meter_serial, profile_obis)
);

drop table meter_ratio_record;

CREATE TABLE meter_ratio_record (
                                    id SERIAL PRIMARY KEY,
                                    meter_serial VARCHAR NOT NULL,
                                    ct_ratio INTEGER NOT NULL,
                                    pt_ratio INTEGER NOT NULL,
                                    ctpt_ratio INTEGER NOT NULL,
                                    read_time TIMESTAMP NOT NULL,
                                    CONSTRAINT uc_meter_serial UNIQUE (meter_serial)
);

CREATE INDEX idx_meter_serial ON meter_ratio_record (meter_serial);