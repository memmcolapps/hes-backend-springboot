-- Domain codes distinct from DLMS event_code (reason-of-operation, manage-token-type).

CREATE TABLE household_reason_of_operation_lookup (
    code        INT PRIMARY KEY,
    description VARCHAR(128) NOT NULL
);

INSERT INTO household_reason_of_operation_lookup (code, description) VALUES
    (0, 'Normal operation after relay connection'),
    (1, 'No credit'),
    (2, 'No emergency credit'),
    (3, 'Over load'),
    (4, 'Test relay opening'),
    (5, 'Meter cover open'),
    (6, 'Terminal cover open'),
    (7, 'Remote disconnection'),
    (8, 'Relay switch after manufacture'),
    (9, 'Over current'),
    (10, 'Test relay closing'),
    (11, 'Over voltage'),
    (12, 'Under voltage'),
    (13, 'Current reverse'),
    (14, 'Reversed polarity'),
    (15, 'Strong magnet'),
    (16, 'Phase missing'),
    (17, 'By pass'),
    (18, 'Current unbalance'),
    (19, 'Neutral missing'),
    (20, 'Remote reconnection'),
    (21, 'Meter box open'),
    (22, 'High temperature'),
    (23, 'Low power factor'),
    (24, 'Reverse sequence'),
    (25, 'Manual disconnection'),
    (26, 'Threshold changed'),
    (27, 'Self-inspection'),
    (28, 'Scheduled disconnection'),
    (29, 'Power off'),
    (30, 'Module cover open'),
    (31, 'swells active power');

CREATE TABLE household_manage_token_type_lookup (
    code        INT PRIMARY KEY,
    description VARCHAR(128) NOT NULL
);

INSERT INTO household_manage_token_type_lookup (code, description) VALUES
    (0, 'Set max power limit'),
    (1, 'Clear credit'),
    (2, 'Set tariff rate'),
    (3, 'Kct 1'),
    (4, 'Kct 2'),
    (5, 'Clear tamper'),
    (6, 'Set phase power imbalance'),
    (8, 'Load new SGC');

ALTER TABLE household_control_event
    ADD COLUMN IF NOT EXISTS reason_of_operation_code INT;

ALTER TABLE household_management_token_event
    ADD COLUMN IF NOT EXISTS manage_token_type_code INT;

-- Replaced by reason_of_operation_code + reason_description (domain lookup, not event_code).
ALTER TABLE household_control_event
    DROP COLUMN IF EXISTS reason_of_operation;

DROP VIEW IF EXISTS vw_event_details;

ALTER TABLE household_management_token_event
    DROP COLUMN IF EXISTS manage_token_type;

CREATE OR REPLACE VIEW vw_event_details
 AS
 SELECT el.meter_serial AS meterno,
    el.event_time,
    el.event_type_id,
    et.name AS event_type,
    ecl.event_name AS event,
    COALESCE(ecl.critical_level, 1) AS critical_level
   FROM event_log el
     LEFT JOIN event_type et ON el.event_type_id = et.id
     LEFT JOIN event_code_lookup ecl ON el.event_type_id = ecl.event_type_id AND el.event_code = ecl.code;
