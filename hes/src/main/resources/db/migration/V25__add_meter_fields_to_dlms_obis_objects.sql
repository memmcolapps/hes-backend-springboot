-- ================================================================
-- Migration: V2__add_meter_fields_to_dlms_obis_objects.sql
-- Author: Engr. Mudashiru
-- Date: 2025-10-27
-- Purpose: Add MeterSerial, MeterModel, and CreateDate columns
--          to the dlms_obis_objects table
-- ================================================================

ALTER TABLE public.dlms_obis_objects
    ADD COLUMN meter_serial VARCHAR(100),
    ADD COLUMN meter_model  VARCHAR(100),
    ADD COLUMN create_date  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;

-- Optional: Add an index to optimize future searches by meter serial/model
CREATE INDEX IF NOT EXISTS idx_dlms_obis_objects_meter_serial
    ON public.dlms_obis_objects (meter_serial);

CREATE INDEX IF NOT EXISTS idx_dlms_obis_objects_meter_model
    ON public.dlms_obis_objects (meter_model);