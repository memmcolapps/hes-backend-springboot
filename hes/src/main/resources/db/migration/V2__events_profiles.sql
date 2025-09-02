-- 1. event_type (lookup table)
-- Stores the high-level category (Standard, Power Grid, Fraud, Control).
CREATE TABLE event_type (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,        -- e.g. "Power Grid Event Logs"
    obis_code VARCHAR(20) NOT NULL,    -- e.g. "0.0.99.98.4.255"
    description TEXT
);

-- 2. event_code_lookup (lookup table)
-- Maps event_code → description for each category.
CREATE TABLE event_code_lookup (
    id SERIAL PRIMARY KEY,
    event_type_id INT NOT NULL REFERENCES event_type(id) ON DELETE CASCADE,
    code INT NOT NULL,                 -- e.g. 101, 201
    description VARCHAR(255) NOT NULL  -- e.g. "Power Failure", "Voltage Sag"
);

-- 3. event_log (main table)
-- Stores actual meter event log entries retrieved from DLMS.
CREATE TABLE event_log (
    id BIGSERIAL PRIMARY KEY,
    meter_serial VARCHAR(50) NOT NULL,         -- meter identifier
    event_type_id INT NOT NULL REFERENCES event_type(id),
    event_code INT NOT NULL,                   -- reference to event_code_lookup.code
    event_time TIMESTAMP NOT NULL,             -- DLMS clock object
    phase VARCHAR(10),                         -- for grid events (e.g. L1, L2, L3, N)
    details JSONB,                             -- extra info (tamper type, operator, etc.)
    created_at TIMESTAMP DEFAULT now()
);

-- 4. Example Seed Data
-- -- Event Types
INSERT INTO event_type (name, obis_code, description)
VALUES
    ('Standard Event Logs', '0.0.99.98.0.255', 'General meter/system events'),
    ('Power Grid Event Logs', '0.0.99.98.4.255', 'Grid-related events'),
    ('Fraud Event Logs', '0.0.99.98.1.255', 'Tamper/fraud events'),
    ('Control Event Logs', '0.0.99.98.2.255', 'Control/operation events');

-- Event Code Lookup (sample)
INSERT INTO event_code_lookup (event_type_id, code, description)
VALUES
    (1, 3, 'Daylight saving time enabled or disabled'),
    (1, 4, 'Clock adjusted(old date/time)'),
    (2, 1, 'Power Down'),
    (2, 2, 'Power Up'),
    (3, 40, 'Terminal cover removed'),
    (3, 41, 'Terminal cover closed'),
    (4, 61, 'Manual connection'),
    (4, 62, 'Remote disconnection');
