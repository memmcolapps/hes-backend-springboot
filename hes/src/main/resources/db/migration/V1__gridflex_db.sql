CREATE SEQUENCE IF NOT EXISTS dlms_obis_objects_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS meter_profile_progress_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS meter_profile_state_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS meter_profile_timestamp_progress_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS meter_ratio_record_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS model_profile_metadata_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS obis_mapping_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS profile_channel_2_readings_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS profile_reading_energy_id_seq INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS scheduler_job_info_job_id_seq INCREMENT BY 1;

CREATE TABLE bands
(
    id             CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    name           VARCHAR(200)                       NOT NULL,
    hour           VARCHAR(200)                       NOT NULL,
    org_id         CHAR(36)                           NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    approve_status VARCHAR  DEFAULT '''''approve''''',
    CONSTRAINT bands_pkey PRIMARY KEY (id)
);

CREATE TABLE bands_version
(
    id             CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    band_id        CHAR(36)                           NOT NULL,
    created_by     CHAR(36)                           NOT NULL,
    approve_by     CHAR(36),
    name           VARCHAR(200)                       NOT NULL,
    hour           VARCHAR(200)                       NOT NULL,
    org_id         CHAR(36)                           NOT NULL,
    description    TEXT                               NOT NULL,
    approve_status VARCHAR(200)                       NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT bands_version_pkey PRIMARY KEY (id)
);

CREATE TABLE clients
(
    client_id UUID DEFAULT gen_random_uuid() NOT NULL,
    client_name   VARCHAR(255),
    client_secret VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE,
    status        VARCHAR(255),
    CONSTRAINT clients_pkey PRIMARY KEY (client_id)
);

CREATE TABLE credit_debit_adjustment
(
    id                 CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    meter_id           CHAR(36)                                              NOT NULL,
    liability_cause_id CHAR(36)                                              NOT NULL,
    debit              numeric(20, 2)                                        NOT NULL,
    balance            numeric(20, 2)                                        NOT NULL,
    status             VARCHAR                     DEFAULT '''''UNPAID''''',
    type               VARCHAR                                               NOT NULL,
    org_id             CHAR(36)                                              NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at         TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    CONSTRAINT credit_debit_adjustment_pkey PRIMARY KEY (id)
);

CREATE TABLE credit_debit_payment
(
    id                  CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    credit_debit_adj_id CHAR(36)                                              NOT NULL,
    credit              numeric(20, 2)                                        NOT NULL,
    payment_method      VARCHAR,
    created_at          TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    org_id              CHAR(36)                                              NOT NULL,
    CONSTRAINT credit_debit_payment_pkey PRIMARY KEY (id)
);

CREATE TABLE customers
(
    id           CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    org_id       CHAR(36)                           NOT NULL,
    firstname    VARCHAR(200)                       NOT NULL,
    lastname     VARCHAR(200)                       NOT NULL,
    customer_id  VARCHAR(200)                       NOT NULL,
    nin          VARCHAR(200)                       NOT NULL,
    status       VARCHAR(200)                       NOT NULL,
    phone_number VARCHAR(200)                       NOT NULL,
    email        VARCHAR(200)                       NOT NULL,
    state        VARCHAR(200)                       NOT NULL,
    city         VARCHAR(200)                       NOT NULL,
    house_no     VARCHAR(200)                       NOT NULL,
    street_name  VARCHAR(200)                       NOT NULL,
    created_at   TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at   TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    tariff       VARCHAR,
    vat          VARCHAR(200)                       NOT NULL,
    CONSTRAINT customers_pkey PRIMARY KEY (id)
);

CREATE TABLE daily_billing_profile
(
    meter_serial             VARCHAR(50)                 NOT NULL,
    meter_model              VARCHAR(50)                 NOT NULL,
    entry_timestamp          TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at              TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_active_energy      DOUBLE PRECISION,
    t1_active_energy         DOUBLE PRECISION,
    t2_active_energy         DOUBLE PRECISION,
    t3_active_energy         DOUBLE PRECISION,
    t4_active_energy         DOUBLE PRECISION,
    total_apparent_energy    DOUBLE PRECISION,
    t1_total_apparent_energy DOUBLE PRECISION,
    t3_total_apparent_energy DOUBLE PRECISION,
    t4_total_apparent_energy DOUBLE PRECISION,
    CONSTRAINT pk_daily_billing PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE debt_percentage
(
    id                 CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    percentage         VARCHAR(20)                        NOT NULL,
    code               VARCHAR(255)                       NOT NULL,
    status             BOOLEAN                            NOT NULL,
    approve_status     VARCHAR(20)                        NOT NULL,
    band_id            CHAR(36)                           NOT NULL,
    amount_start_range VARCHAR                            NOT NULL,
    amount_end_range   VARCHAR                            NOT NULL,
    org_id             CHAR(36)                           NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT debt_percentage_pkey PRIMARY KEY (id)
);

CREATE TABLE debt_percentage_version
(
    id                 CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    debt_percentage_id CHAR(36)                           NOT NULL,
    percentage         VARCHAR(20)                        NOT NULL,
    code               VARCHAR(255)                       NOT NULL,
    status             BOOLEAN                            NOT NULL,
    approve_status     VARCHAR(20),
    band_id            CHAR(36)                           NOT NULL,
    amount_start_range VARCHAR                            NOT NULL,
    amount_end_range   VARCHAR                            NOT NULL,
    description        TEXT                               NOT NULL,
    created_by         CHAR(36)                           NOT NULL,
    approve_by         CHAR(36),
    org_id             CHAR(36)                           NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT debt_percentage_version_pkey PRIMARY KEY (id)
);

CREATE TABLE dlms_obis_objects
(
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    access_rights   VARCHAR(255),
    attribute_count INTEGER                                 NOT NULL,
    class_id        INTEGER                                 NOT NULL,
    obis_code       VARCHAR(255),
    scaler          VARCHAR(255),
    type            VARCHAR(255),
    unit            VARCHAR(255),
    version         INTEGER                                 NOT NULL,
    CONSTRAINT dlms_obis_objects_pkey PRIMARY KEY (id)
);

CREATE TABLE group_permissions
(
    group_id      CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,
    org_id        CHAR(36) NOT NULL,
    CONSTRAINT group_permissions_pkey PRIMARY KEY (group_id, permission_id)
);

CREATE TABLE groups
(
    id         CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    title      VARCHAR(100)                       NOT NULL,
    org_id     CHAR(36)                           NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT groups_pkey PRIMARY KEY (id)
);

CREATE TABLE liability_cause
(
    id             CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    name           VARCHAR(255)                       NOT NULL,
    code           VARCHAR(255)                       NOT NULL,
    status         BOOLEAN                            NOT NULL,
    approve_status VARCHAR(20)                        NOT NULL,
    org_id         CHAR(36)                           NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT liability_cause_pkey PRIMARY KEY (id)
);

CREATE TABLE liability_cause_version
(
    id                 CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    liability_cause_id CHAR(36)                           NOT NULL,
    name               VARCHAR(255)                       NOT NULL,
    code               VARCHAR(255)                       NOT NULL,
    status             BOOLEAN                            NOT NULL,
    approve_status     VARCHAR(20),
    description        TEXT                               NOT NULL,
    created_by         CHAR(36)                           NOT NULL,
    approve_by         CHAR(36),
    org_id             CHAR(36)                           NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT liability_cause_version_pkey PRIMARY KEY (id)
);

CREATE TABLE manufacturers
(
    id              CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    name            VARCHAR(255)                       NOT NULL,
    manufacturer_id VARCHAR(255)                       NOT NULL,
    state           VARCHAR(50)                        NOT NULL,
    email           VARCHAR(255),
    contact_person  VARCHAR(255)                       NOT NULL,
    phone_no        VARCHAR(255)                       NOT NULL,
    org_id          CHAR(36)                           NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at      TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT manufacturers_pkey PRIMARY KEY (id)
);

CREATE TABLE md_meters_info
(
    id              CHAR(36) DEFAULT gen_random_uuid()                                               NOT NULL,
    meter_id        CHAR(36)                                                                         NOT NULL,
    org_id          CHAR(36)                                                                         NOT NULL,
    ct_ratio_num    BIGINT   DEFAULT 0                                                                 NOT NULL,
    volt_ratio_num  BIGINT   DEFAULT 0 NOT NULL,
    multiplier      BIGINT   DEFAULT 0 NOT NULL,
    initial_reading BIGINT   DEFAULT 0 NOT NULL,
    latitude        VARCHAR  DEFAULT '0.0000000'                                             NOT NULL,
    longitude       VARCHAR  DEFAULT '0.0000000'                                             NOT NULL,
    CONSTRAINT md_meters_info_pkey PRIMARY KEY (id)
);

CREATE TABLE md_meters_info_version
(
    id               CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    meter_id         CHAR(36)                           NOT NULL,
    org_id           CHAR(36)                           NOT NULL,
    ct_ratio_num     BIGINT   DEFAULT 0,
    ct_ratio_denom   BIGINT   DEFAULT 0,
    volt_ratio_num   BIGINT   DEFAULT 0,
    volt_ratio_denom BIGINT   DEFAULT 0,
    multiplier       BIGINT   DEFAULT 0,
    meter_rating     BIGINT   DEFAULT 0,
    initial_reading  BIGINT   DEFAULT 0,
    dial             BIGINT   DEFAULT 0,
    latitude         VARCHAR  DEFAULT '''''0.0000000''''',
    longitude        VARCHAR  DEFAULT '''''0.0000000''''',
    approve_status   VARCHAR                            NOT NULL,
    created_by       CHAR(36)                           NOT NULL,
    approve_by       CHAR(36),
    description      TEXT                               NOT NULL,
    CONSTRAINT md_meters_info_version_pkey PRIMARY KEY (id)
);

CREATE TABLE meter_assign_locations
(
    id          CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    meter_id    CHAR(36)                                              NOT NULL,
    org_id      CHAR(36)                                              NOT NULL,
    state       VARCHAR(200)                                          NOT NULL,
    city        VARCHAR(200)                                          NOT NULL,
    house_no    VARCHAR(200)                                          NOT NULL,
    street_name VARCHAR(200)                                          NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    updated_at  TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    CONSTRAINT meter_assign_locations_pkey PRIMARY KEY (id)
);

CREATE TABLE meter_assign_locations_version
(
    id             CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    meter_id       CHAR(36)                                              NOT NULL,
    org_id         CHAR(36)                                              NOT NULL,
    state          VARCHAR(200)                                          NOT NULL,
    city           VARCHAR(200)                                          NOT NULL,
    house_no       VARCHAR(200)                                          NOT NULL,
    street_name    VARCHAR(200)                                          NOT NULL,
    approve_status VARCHAR(200)                                          NOT NULL,
    created_by     VARCHAR(200)                                          NOT NULL,
    approve_by     VARCHAR(200),
    description    TEXT                                                  NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    CONSTRAINT meter_assign_locations_version_pkey PRIMARY KEY (id)
);

CREATE TABLE meter_profile_progress
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    last_entry_index INTEGER                                 NOT NULL,
    meter_serial     VARCHAR(64)                             NOT NULL,
    profile_obis     VARCHAR(32)                             NOT NULL,
    updated_at       TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT meter_profile_progress_pkey PRIMARY KEY (id)
);

CREATE TABLE meter_profile_state
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY   NOT NULL,
    meter_serial       VARCHAR(20)                               NOT NULL,
    profile_obis       VARCHAR(20)                               NOT NULL,
    last_timestamp     TIMESTAMP WITHOUT TIME ZONE,
    capture_period_sec INTEGER,
    updated_at         TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT meter_profile_state_pkey PRIMARY KEY (id)
);

CREATE TABLE meter_profile_timestamp_progress
(
    id                     BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    last_profile_timestamp TIMESTAMP WITHOUT TIME ZONE,
    meter_serial           VARCHAR(255)                            NOT NULL,
    profile_obis           VARCHAR(255)                            NOT NULL,
    updated_at             TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT meter_profile_timestamp_progress_pkey PRIMARY KEY (id)
);

CREATE TABLE meter_ratio_record
(
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    meter_serial VARCHAR                                 NOT NULL,
    ct_ratio     INTEGER                                 NOT NULL,
    pt_ratio     INTEGER                                 NOT NULL,
    ctpt_ratio   INTEGER                                 NOT NULL,
    read_time    TIMESTAMP WITHOUT TIME ZONE             NOT NULL,
    CONSTRAINT meter_ratio_record_pkey PRIMARY KEY (id)
);

CREATE TABLE meters
(
    id                 CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    org_id             CHAR(36)                           NOT NULL,
    account_number     VARCHAR(200),
    node_id            CHAR(36),
    sim_number         VARCHAR(200)                       NOT NULL,
    meter_category     VARCHAR(200)                       NOT NULL,
    meter_class        VARCHAR(200)                       NOT NULL,
    meter_type         VARCHAR(200)                       NOT NULL,
    status             BOOLEAN                            NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at         TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    customer_id        VARCHAR(15),
    meter_number       VARCHAR(200)                       NOT NULL,
    type               VARCHAR                            NOT NULL,
    cin                VARCHAR,
    old_sgc            VARCHAR  DEFAULT '''''0'''''       NOT NULL,
    new_sgc            VARCHAR  DEFAULT '''''0'''''       NOT NULL,
    new_krn            VARCHAR  DEFAULT '''''0'''''       NOT NULL,
    old_krn            VARCHAR  DEFAULT '''''0'''''       NOT NULL,
    energy_type        VARCHAR,
    fixed_energy       VARCHAR,
    old_tariff_index   BIGINT   DEFAULT 1                 NOT NULL,
    new_tariff_index   BIGINT   DEFAULT 1                 NOT NULL,
    dss                CHAR(36),
    meter_manufacturer CHAR(36)                           NOT NULL,
    activate_status    BOOLEAN  DEFAULT TRUE              NOT NULL,
    tariff             CHAR(36),
    smart_status       BOOLEAN  DEFAULT FALSE,
    meter_model        VARCHAR                            NOT NULL,
    image              VARCHAR,
    CONSTRAINT meters_pkey PRIMARY KEY (id)
);

CREATE TABLE meters_version
(
    id                 CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    org_id             CHAR(36)                                              NOT NULL,
    meter_id           CHAR(36)                                              NOT NULL,
    meter_number       VARCHAR(50)                                           NOT NULL,
    old_meter_number   VARCHAR(50),
    account_number     VARCHAR(200),
    node_id            CHAR(36),
    dss                CHAR(36),
    cin                VARCHAR,
    customer_id        VARCHAR(200),
    type               VARCHAR(50)                                           NOT NULL,
    sim_number         VARCHAR(100)                                          NOT NULL,
    old_sgc            VARCHAR(50)                 DEFAULT '''''0'''''       NOT NULL,
    new_sgc            VARCHAR(50)                 DEFAULT '''''0'''''       NOT NULL,
    old_krn            VARCHAR(50)                 DEFAULT '''''0'''''       NOT NULL,
    new_krn            VARCHAR(50)                 DEFAULT '''''0'''''       NOT NULL,
    old_tariff_index   BIGINT                      DEFAULT 0                 NOT NULL,
    new_tariff_index   BIGINT                      DEFAULT 0                 NOT NULL,
    energy_type        VARCHAR(50),
    fixed_energy       VARCHAR(50),
    meter_type         VARCHAR(50)                                           NOT NULL,
    meter_category     VARCHAR(50)                                           NOT NULL,
    meter_class        VARCHAR(50)                                           NOT NULL,
    meter_manufacturer CHAR(36)                                              NOT NULL,
    status             BOOLEAN                                               NOT NULL,
    approve_status     VARCHAR                     DEFAULT '''''pending''''' NOT NULL,
    created_by         CHAR(36)                                              NOT NULL,
    approve_by         CHAR(36),
    description        TEXT                                                  NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    updated_at         TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    tariff             CHAR(36),
    image              VARCHAR,
    activate_status    BOOLEAN,
    CONSTRAINT meters_version_pkey PRIMARY KEY (id)
);

CREATE TABLE model_profile_metadata
(
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    attribute_index INTEGER                                 NOT NULL,
    capture_obis    VARCHAR(32)                             NOT NULL,
    class_id        INTEGER                                 NOT NULL,
    description     VARCHAR(255),
    meter_model     VARCHAR(64)                             NOT NULL,
    profile_obis    VARCHAR(32)                             NOT NULL,
    scaler          DOUBLE PRECISION                        NOT NULL,
    unit            VARCHAR(255),
    capture_index   INTEGER      DEFAULT 0                  NOT NULL,
    column_name     VARCHAR(100) DEFAULT ''''''''''         NOT NULL,
    multiply_by     VARCHAR(100) DEFAULT '''''CTPT''''',
    type            VARCHAR(32),
    CONSTRAINT model_profile_metadata_pkey PRIMARY KEY (id)
);

CREATE TABLE model_profile_metadata_captured_obis
(
    model_profile_metadata_id BIGINT NOT NULL,
    captured_obis             VARCHAR(255)
);

CREATE TABLE model_profile_metadata_scalers
(
    model_profile_metadata_id BIGINT NOT NULL,
    scalers                   INTEGER
);

CREATE TABLE modules
(
    id       CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    name     VARCHAR(100)                       NOT NULL,
    access   BOOLEAN                            NOT NULL,
    org_id   CHAR(36)                           NOT NULL,
    group_id CHAR(36)                           NOT NULL,
    CONSTRAINT modules_pkey PRIMARY KEY (id)
);

CREATE TABLE monthly_billing_profile
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_monthly_billing PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202409
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202409_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202410
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202410_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202411
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202411_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202412
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202412_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202501
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202501_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202502
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202502_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202503
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202503_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202504
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202504_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202505
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202505_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202506
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202506_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202507
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202507_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_billing_profile_202508
(
    meter_serial                 VARCHAR(50)                 NOT NULL,
    meter_model                  VARCHAR(50)                 NOT NULL,
    entry_timestamp              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    received_at                  TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    total_absolute_active_energy DOUBLE PRECISION,
    export_active_energy         DOUBLE PRECISION,
    import_active_energy         DOUBLE PRECISION,
    import_reactive_energy       DOUBLE PRECISION,
    export_reactive_energy       DOUBLE PRECISION,
    remaining_credit_amount      DOUBLE PRECISION,
    import_active_md             DOUBLE PRECISION,
    import_active_md_time        TIMESTAMP WITHOUT TIME ZONE,
    t1_active_energy             DOUBLE PRECISION,
    t2_active_energy             DOUBLE PRECISION,
    t3_active_energy             DOUBLE PRECISION,
    t4_active_energy             DOUBLE PRECISION,
    total_active_energy          DOUBLE PRECISION,
    total_apparent_energy        DOUBLE PRECISION,
    t1_total_apparent_energy     DOUBLE PRECISION,
    t2_total_apparent_energy     DOUBLE PRECISION,
    t3_total_apparent_energy     DOUBLE PRECISION,
    t4_total_apparent_energy     DOUBLE PRECISION,
    active_maximum_demand        DOUBLE PRECISION,
    total_apparent_demand        DOUBLE PRECISION,
    total_apparent_demand_time   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT monthly_billing_profile_202508_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE monthly_consumption
(
    meter_serial    VARCHAR(64)                               NOT NULL,
    month_start     date                                      NOT NULL,
    meter_model     VARCHAR(64),
    prev_value_kwh  DOUBLE PRECISION,
    curr_value_kwh  DOUBLE PRECISION,
    consumption_kwh DOUBLE PRECISION,
    created_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT pk_monthly_consumption PRIMARY KEY (meter_serial, month_start)
);

CREATE TABLE monthly_consumption_202412
(
    meter_serial    VARCHAR(64)                               NOT NULL,
    month_start     date                                      NOT NULL,
    meter_model     VARCHAR(64),
    prev_value_kwh  DOUBLE PRECISION,
    curr_value_kwh  DOUBLE PRECISION,
    consumption_kwh DOUBLE PRECISION,
    created_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT monthly_consumption_202412_pkey PRIMARY KEY (meter_serial, month_start)
);

CREATE TABLE monthly_consumption_202501
(
    meter_serial    VARCHAR(64)                               NOT NULL,
    month_start     date                                      NOT NULL,
    meter_model     VARCHAR(64),
    prev_value_kwh  DOUBLE PRECISION,
    curr_value_kwh  DOUBLE PRECISION,
    consumption_kwh DOUBLE PRECISION,
    created_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT monthly_consumption_202501_pkey PRIMARY KEY (meter_serial, month_start)
);

CREATE TABLE monthly_consumption_202504
(
    meter_serial    VARCHAR(64)                               NOT NULL,
    month_start     date                                      NOT NULL,
    meter_model     VARCHAR(64),
    prev_value_kwh  DOUBLE PRECISION,
    curr_value_kwh  DOUBLE PRECISION,
    consumption_kwh DOUBLE PRECISION,
    created_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT monthly_consumption_202504_pkey PRIMARY KEY (meter_serial, month_start)
);

CREATE TABLE monthly_consumption_202505
(
    meter_serial    VARCHAR(64)                               NOT NULL,
    month_start     date                                      NOT NULL,
    meter_model     VARCHAR(64),
    prev_value_kwh  DOUBLE PRECISION,
    curr_value_kwh  DOUBLE PRECISION,
    consumption_kwh DOUBLE PRECISION,
    created_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT monthly_consumption_202505_pkey PRIMARY KEY (meter_serial, month_start)
);

CREATE TABLE monthly_consumption_202506
(
    meter_serial    VARCHAR(64)                               NOT NULL,
    month_start     date                                      NOT NULL,
    meter_model     VARCHAR(64),
    prev_value_kwh  DOUBLE PRECISION,
    curr_value_kwh  DOUBLE PRECISION,
    consumption_kwh DOUBLE PRECISION,
    created_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT monthly_consumption_202506_pkey PRIMARY KEY (meter_serial, month_start)
);

CREATE TABLE nodes
(
    id        CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    name      VARCHAR(255)                       NOT NULL,
    parent_id CHAR(36),
    org_id    CHAR(36)                           NOT NULL,
    CONSTRAINT nodes_pkey PRIMARY KEY (id)
);

CREATE TABLE obis_mapping
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    obis_code_combined VARCHAR(255),
    attribute_index    INTEGER                                 NOT NULL,
    class_id           INTEGER                                 NOT NULL,
    data_index         INTEGER                                 NOT NULL,
    data_type          VARCHAR(255),
    description        VARCHAR(255)                            NOT NULL,
    group_name         VARCHAR(255),
    obis_code          VARCHAR(255)                            NOT NULL,
    scaler             DOUBLE PRECISION,
    unit               VARCHAR(255),
    model              VARCHAR(50),
    purpose            VARCHAR(30),
    CONSTRAINT obis_mapping_pkey PRIMARY KEY (id)
);

CREATE TABLE organizations
(
    id            CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    business_name VARCHAR(100)                       NOT NULL,
    postal_code   VARCHAR(100)                       NOT NULL,
    address       VARCHAR(100)                       NOT NULL,
    country       VARCHAR(100)                       NOT NULL,
    state         VARCHAR(100)                       NOT NULL,
    city          VARCHAR(100)                       NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at    TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    status        BOOLEAN  DEFAULT TRUE,
    user_id       CHAR(36),
    image         VARCHAR,
    CONSTRAINT organizations_pkey PRIMARY KEY (id)
);

CREATE TABLE payment_mode
(
    id                  CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    meter_id            CHAR(36)                                              NOT NULL,
    org_id              CHAR(36)                                              NOT NULL,
    credit_payment_mode VARCHAR                                               NOT NULL,
    credit_payment_plan VARCHAR                                               NOT NULL,
    debit_payment_mode  VARCHAR                                               NOT NULL,
    debit_payment_plan  VARCHAR                                               NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    updated_at          TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    status              BOOLEAN                                               NOT NULL,
    CONSTRAINT payment_mode_pkey PRIMARY KEY (id)
);

CREATE TABLE payment_mode_version
(
    id                  CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    meter_id            CHAR(36)                                              NOT NULL,
    org_id              CHAR(36)                                              NOT NULL,
    credit_payment_mode VARCHAR                                               NOT NULL,
    credit_payment_plan VARCHAR                                               NOT NULL,
    debit_payment_mode  VARCHAR                                               NOT NULL,
    debit_payment_plan  VARCHAR                                               NOT NULL,
    status              BOOLEAN                                               NOT NULL,
    approve_status      VARCHAR                                               NOT NULL,
    created_by          CHAR(36)                                              NOT NULL,
    approve_by          CHAR(36),
    description         TEXT                                                  NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    CONSTRAINT payment_mode_version_pkey PRIMARY KEY (id)
);

CREATE TABLE permissions
(
    id      CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    view    BOOLEAN                            NOT NULL,
    edit    BOOLEAN                            NOT NULL,
    approve BOOLEAN                            NOT NULL,
    disable BOOLEAN                            NOT NULL,
    org_id  CHAR(36)                           NOT NULL,
    CONSTRAINT permissions_pkey PRIMARY KEY (id)
);

CREATE TABLE portal_roles
(
    id        CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    user_id   CHAR(36)                           NOT NULL,
    user_role VARCHAR(255)                       NOT NULL,
    CONSTRAINT portal_roles_pkey PRIMARY KEY (id)
);

CREATE TABLE portal_users
(
    id          CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    firstname   VARCHAR(255)                       NOT NULL,
    lastname    VARCHAR(255)                       NOT NULL,
    email       VARCHAR(255)                       NOT NULL,
    status      BOOLEAN                            NOT NULL,
    active      BOOLEAN                            NOT NULL,
    department  VARCHAR                            NOT NULL,
    password    VARCHAR(500)                       NOT NULL,
    last_active TIMESTAMP WITHOUT TIME ZONE,
    created_at  TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at  TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT portal_users_pkey PRIMARY KEY (id)
);

CREATE TABLE profile_channel_2_readings
(
    id                         BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    total_import_active_energy DOUBLE PRECISION,
    entry_index                BIGINT                                  NOT NULL,
    entry_timestamp            TIMESTAMP WITHOUT TIME ZONE             NOT NULL,
    meter_serial               VARCHAR(64)                             NOT NULL,
    model_number               VARCHAR(64)                             NOT NULL,
    raw_data                   TEXT,
    total_export_active_energy DOUBLE PRECISION,
    received_at                TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT profile_channel_2_readings_pkey PRIMARY KEY (id)
);

CREATE TABLE profile_channel_one
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_profile_channel_one PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202412
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202412_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202501
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202501_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202502
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202502_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202503
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202503_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202504
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202504_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202505
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202505_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202506
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202506_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202507
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202507_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_channel_one_202508
(
    meter_serial                       VARCHAR(50)                 NOT NULL,
    model_number                       VARCHAR(50)                 NOT NULL,
    entry_timestamp                    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    meter_health_indicator             INTEGER,
    total_instantaneous_active_power   DOUBLE PRECISION,
    total_instantaneous_apparent_power DOUBLE PRECISION,
    l1_current_harmonic_thd            DOUBLE PRECISION,
    l2_current_harmonic_thd            DOUBLE PRECISION,
    l3_current_harmonic_thd            DOUBLE PRECISION,
    l1_voltage_harmonic_thd            DOUBLE PRECISION,
    l2_voltage_harmonic_thd            DOUBLE PRECISION,
    l3_voltage_harmonic_thd            DOUBLE PRECISION,
    received_at                        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_channel_one_202508_pkey PRIMARY KEY (meter_serial, entry_timestamp)
);

CREATE TABLE profile_reading_energy
(
    id        BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    energy    DOUBLE PRECISION                        NOT NULL,
    meter_id  VARCHAR(255),
    timestamp TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT profile_reading_energy_pkey PRIMARY KEY (id)
);

CREATE TABLE qrtz_blob_triggers
(
    sched_name    VARCHAR(120) NOT NULL,
    trigger_name  VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data     BYTEA,
    CONSTRAINT pk_qrtz_blob_triggers PRIMARY KEY (sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_calendars
(
    sched_name    VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar      BYTEA        NOT NULL,
    CONSTRAINT pk_qrtz_calendars PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE qrtz_cron_triggers
(
    sched_name      VARCHAR(120) NOT NULL,
    trigger_name    VARCHAR(200) NOT NULL,
    trigger_group   VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id    VARCHAR(80),
    CONSTRAINT pk_qrtz_cron_triggers PRIMARY KEY (sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_fired_triggers
(
    sched_name        VARCHAR(120) NOT NULL,
    entry_id          VARCHAR(95)  NOT NULL,
    trigger_name      VARCHAR(200) NOT NULL,
    trigger_group     VARCHAR(200) NOT NULL,
    instance_name     VARCHAR(200) NOT NULL,
    fired_time        BIGINT       NOT NULL,
    sched_time        BIGINT       NOT NULL,
    priority          INTEGER      NOT NULL,
    state             VARCHAR(16)  NOT NULL,
    job_name          VARCHAR(200),
    job_group         VARCHAR(200),
    is_nonconcurrent  BOOLEAN,
    requests_recovery BOOLEAN,
    CONSTRAINT pk_qrtz_fired_triggers PRIMARY KEY (sched_name, entry_id)
);

CREATE TABLE qrtz_job_details
(
    sched_name        VARCHAR(120) NOT NULL,
    job_name          VARCHAR(200) NOT NULL,
    job_group         VARCHAR(200) NOT NULL,
    description       VARCHAR(250),
    job_class_name    VARCHAR(250) NOT NULL,
    is_durable        BOOLEAN      NOT NULL,
    is_nonconcurrent  BOOLEAN      NOT NULL,
    is_update_data    BOOLEAN      NOT NULL,
    requests_recovery BOOLEAN      NOT NULL,
    job_data          BYTEA,
    CONSTRAINT pk_qrtz_job_details PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE TABLE qrtz_locks
(
    sched_name VARCHAR(120) NOT NULL,
    lock_name  VARCHAR(40)  NOT NULL,
    CONSTRAINT pk_qrtz_locks PRIMARY KEY (sched_name, lock_name)
);

CREATE TABLE qrtz_paused_trigger_grps
(
    sched_name    VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    CONSTRAINT pk_qrtz_paused_trigger_grps PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE qrtz_scheduler_state
(
    sched_name        VARCHAR(120) NOT NULL,
    instance_name     VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT       NOT NULL,
    checkin_interval  BIGINT       NOT NULL,
    CONSTRAINT pk_qrtz_scheduler_state PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE qrtz_simple_triggers
(
    sched_name      VARCHAR(120) NOT NULL,
    trigger_name    VARCHAR(200) NOT NULL,
    trigger_group   VARCHAR(200) NOT NULL,
    repeat_count    BIGINT       NOT NULL,
    repeat_interval BIGINT       NOT NULL,
    times_triggered BIGINT       NOT NULL,
    CONSTRAINT pk_qrtz_simple_triggers PRIMARY KEY (sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_simprop_triggers
(
    sched_name    VARCHAR(120) NOT NULL,
    trigger_name  VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    str_prop_1    VARCHAR(512),
    str_prop_2    VARCHAR(512),
    str_prop_3    VARCHAR(512),
    int_prop_1    INTEGER,
    int_prop_2    INTEGER,
    long_prop_1   BIGINT,
    long_prop_2   BIGINT,
    dec_prop_1    numeric(13, 4),
    dec_prop_2    numeric(13, 4),
    bool_prop_1   BOOLEAN,
    bool_prop_2   BOOLEAN,
    CONSTRAINT pk_qrtz_simprop_triggers PRIMARY KEY (sched_name, trigger_name, trigger_group)
);

CREATE TABLE qrtz_triggers
(
    sched_name     VARCHAR(120) NOT NULL,
    trigger_name   VARCHAR(200) NOT NULL,
    trigger_group  VARCHAR(200) NOT NULL,
    job_name       VARCHAR(200) NOT NULL,
    job_group      VARCHAR(200) NOT NULL,
    description    VARCHAR(250),
    next_fire_time BIGINT,
    prev_fire_time BIGINT,
    priority       INTEGER,
    trigger_state  VARCHAR(16)  NOT NULL,
    trigger_type   VARCHAR(8)   NOT NULL,
    start_time     BIGINT       NOT NULL,
    end_time       BIGINT,
    calendar_name  VARCHAR(200),
    misfire_instr  SMALLINT,
    job_data       BYTEA,
    CONSTRAINT pk_qrtz_triggers PRIMARY KEY (sched_name, trigger_name, trigger_group)
);

CREATE TABLE region_bhub_service_centers
(
    id             CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    org_id         CHAR(36)                           NOT NULL,
    region_id      VARCHAR(255)                       NOT NULL,
    node_id        CHAR(36)                           NOT NULL,
    name           VARCHAR(255)                       NOT NULL,
    phone_number   VARCHAR(255)                       NOT NULL,
    email          VARCHAR(255)                       NOT NULL,
    contact_person VARCHAR(255)                       NOT NULL,
    address        VARCHAR(255)                       NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    type           VARCHAR                            NOT NULL,
    parent_id      CHAR(36),
    CONSTRAINT regions_pkey PRIMARY KEY (id)
);

CREATE TABLE scheduler_job_info
(
    job_id          BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    cron_expression VARCHAR(255),
    cron_job        BOOLEAN,
    description     VARCHAR(255),
    interface_name  VARCHAR(255),
    job_class       VARCHAR(255),
    job_group       VARCHAR(255),
    job_name        VARCHAR(255),
    job_status      VARCHAR(255),
    repeat_time     BIGINT,
    CONSTRAINT scheduler_job_info_pkey PRIMARY KEY (job_id)
);

CREATE TABLE smart_meter_info
(
    id             CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    meter_id       CHAR(36)                           NOT NULL,
    org_id         CHAR(36)                           NOT NULL,
    meter_model    VARCHAR                            NOT NULL,
    protocol       VARCHAR                            NOT NULL,
    authentication VARCHAR                            NOT NULL,
    password       VARCHAR                            NOT NULL,
    CONSTRAINT smart_meter_info_pkey PRIMARY KEY (id)
);

CREATE TABLE smart_meter_info_version
(
    id             CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    meter_id       CHAR(36)                           NOT NULL,
    org_id         CHAR(36)                           NOT NULL,
    meter_model    VARCHAR                            NOT NULL,
    protocol       VARCHAR                            NOT NULL,
    authentication VARCHAR                            NOT NULL,
    password       VARCHAR                            NOT NULL,
    created_by     CHAR(36)                           NOT NULL,
    approve_by     CHAR(36),
    description    TEXT                               NOT NULL,
    approve_status VARCHAR  DEFAULT '''''pending''''',
    CONSTRAINT smart_meter_info_version_pkey PRIMARY KEY (id)
);

CREATE TABLE submodules
(
    id        CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    name      VARCHAR(100)                       NOT NULL,
    module_id CHAR(36)                           NOT NULL,
    access    BOOLEAN                            NOT NULL,
    org_id    CHAR(36)                           NOT NULL,
    CONSTRAINT submodules_pkey PRIMARY KEY (id)
);

CREATE TABLE substation_trans_feeder_lines
(
    id             CHAR(36)     DEFAULT gen_random_uuid() NOT NULL,
    node_id        CHAR(36)                               NOT NULL,
    org_id         CHAR(36)                               NOT NULL,
    name           VARCHAR(255)                           NOT NULL,
    serial_no      VARCHAR(255)                           NOT NULL,
    phone_number   VARCHAR(255)                           NOT NULL,
    email          VARCHAR(255)                           NOT NULL,
    contact_person VARCHAR(255)                           NOT NULL,
    address        VARCHAR(255)                           NOT NULL,
    status         BOOLEAN                                NOT NULL,
    voltage        VARCHAR(255)                           NOT NULL,
    latitude       VARCHAR(255) DEFAULT '''''0.0000000''''',
    longitude      VARCHAR(255) DEFAULT '''''0.0000000''''',
    description    TEXT,
    created_at     TIMESTAMP WITHOUT TIME ZONE            NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE            NOT NULL,
    type           VARCHAR                                NOT NULL,
    asset_id       VARCHAR,
    parent_id      CHAR(36)                               NOT NULL,
    CONSTRAINT substations_pkey PRIMARY KEY (id)
);

CREATE TABLE tariffs
(
    id             CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    org_id         CHAR(36)                           NOT NULL,
    name           VARCHAR(100)                       NOT NULL,
    tariff_id      VARCHAR(20)                        NOT NULL,
    tariff_type    VARCHAR(100)                       NOT NULL,
    effective_date VARCHAR(100),
    tariff_rate    VARCHAR(100)                       NOT NULL,
    band           VARCHAR(100)                       NOT NULL,
    status         BOOLEAN                            NOT NULL,
    approve_status VARCHAR(100)                       NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    CONSTRAINT tariffs_pkey PRIMARY KEY (id)
);

CREATE TABLE tariffs_version
(
    id             CHAR(36)                    DEFAULT gen_random_uuid() NOT NULL,
    org_id         CHAR(36)                                              NOT NULL,
    t_id           CHAR(36)                                              NOT NULL,
    created_by     CHAR(36)                                              NOT NULL,
    approved_by    CHAR(36),
    name           VARCHAR(100)                                          NOT NULL,
    tariff_id      VARCHAR(100)                                          NOT NULL,
    tariff_type    VARCHAR(100)                                          NOT NULL,
    effective_date VARCHAR(100)                                          NOT NULL,
    tariff_rate    VARCHAR(100)                                          NOT NULL,
    band           VARCHAR(100)                                          NOT NULL,
    status         BOOLEAN                                               NOT NULL,
    approve_status VARCHAR(100),
    description    TEXT                                                  NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    updated_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()             NOT NULL,
    CONSTRAINT tariffs_version_pkey PRIMARY KEY (id)
);

CREATE TABLE user_groups
(
    user_id  CHAR(36) NOT NULL,
    group_id CHAR(36) NOT NULL,
    org_id   CHAR(36) NOT NULL,
    CONSTRAINT user_groups_pkey PRIMARY KEY (user_id, group_id)
);

CREATE TABLE users
(
    id           CHAR(36) DEFAULT gen_random_uuid() NOT NULL,
    org_id       CHAR(36)                           NOT NULL,
    firstname    VARCHAR(255)                       NOT NULL,
    lastname     VARCHAR(255)                       NOT NULL,
    email        VARCHAR(255)                       NOT NULL,
    node_id      CHAR(36)                           NOT NULL,
    status       BOOLEAN                            NOT NULL,
    active       BOOLEAN                            NOT NULL,
    password     VARCHAR(500)                       NOT NULL,
    last_active  TIMESTAMP WITHOUT TIME ZONE,
    created_at   TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    updated_at   TIMESTAMP WITHOUT TIME ZONE        NOT NULL,
    phone_number VARCHAR(15),
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

ALTER TABLE bands
    ADD CONSTRAINT bands_name_key UNIQUE (name);

ALTER TABLE customers
    ADD CONSTRAINT customers_account_number_key UNIQUE (customer_id);

ALTER TABLE customers
    ADD CONSTRAINT customers_email_key UNIQUE (email);

ALTER TABLE customers
    ADD CONSTRAINT customers_nin_key UNIQUE (nin);

ALTER TABLE customers
    ADD CONSTRAINT customers_phone_number_key UNIQUE (phone_number);

ALTER TABLE debt_percentage
    ADD CONSTRAINT debt_percentage_code_key UNIQUE (code);

ALTER TABLE liability_cause
    ADD CONSTRAINT liability_cause_code_key UNIQUE (code);

ALTER TABLE liability_cause
    ADD CONSTRAINT liability_cause_name_key UNIQUE (name);

ALTER TABLE manufacturers
    ADD CONSTRAINT manufacturers_email_key UNIQUE (email);

ALTER TABLE manufacturers
    ADD CONSTRAINT manufacturers_manufacturer_id_key UNIQUE (manufacturer_id);

ALTER TABLE manufacturers
    ADD CONSTRAINT manufacturers_name_key UNIQUE (name);

ALTER TABLE meters
    ADD CONSTRAINT meters_meter_number_key UNIQUE (meter_number);

ALTER TABLE organizations
    ADD CONSTRAINT organizations_business_name_key UNIQUE (business_name);

ALTER TABLE region_bhub_service_centers
    ADD CONSTRAINT regions_email_key UNIQUE (email);

ALTER TABLE substation_trans_feeder_lines
    ADD CONSTRAINT substations_email_key UNIQUE (email);

ALTER TABLE substation_trans_feeder_lines
    ADD CONSTRAINT substations_serial_no_key UNIQUE (serial_no);

ALTER TABLE tariffs
    ADD CONSTRAINT tariffs_name_key UNIQUE (name);

ALTER TABLE meter_ratio_record
    ADD CONSTRAINT uc_meter_serial UNIQUE (meter_serial);

ALTER TABLE model_profile_metadata
    ADD CONSTRAINT uk_model_profile_capture UNIQUE (meter_model, profile_obis, capture_obis, attribute_index);

ALTER TABLE profile_channel_2_readings
    ADD CONSTRAINT uk_profile2_unique UNIQUE (meter_serial, entry_timestamp);

ALTER TABLE meter_profile_timestamp_progress
    ADD CONSTRAINT ukbt84m3a8qcm0vikhijbju7pkh UNIQUE (meter_serial, profile_obis);

ALTER TABLE meter_profile_progress
    ADD CONSTRAINT ukcart4332ovg4efgqaaj3jw29g UNIQUE (meter_serial, profile_obis);

ALTER TABLE meters
    ADD CONSTRAINT unique_account_no UNIQUE (account_number);

ALTER TABLE meters
    ADD CONSTRAINT unique_cin UNIQUE (cin);

ALTER TABLE tariffs
    ADD CONSTRAINT unique_tariff_id UNIQUE (tariff_id);

ALTER TABLE meter_profile_state
    ADD CONSTRAINT ux_meter_profile_state_serial_obis UNIQUE (meter_serial, profile_obis);

CREATE INDEX idx_model_profile ON model_profile_metadata (meter_model, profile_obis);

CREATE INDEX idx_obis_code ON obis_mapping (obis_code);

CREATE INDEX idx_profile2_entry_index ON profile_channel_2_readings (entry_index);

CREATE INDEX idx_profile2_meter ON profile_channel_2_readings (meter_serial);

CREATE INDEX idx_profile2_timestamp ON profile_channel_2_readings (entry_timestamp);

CREATE INDEX idx_profile_metadata_meter_model ON model_profile_metadata (meter_model);

CREATE INDEX idx_profile_metadata_obis_code ON model_profile_metadata (capture_obis);

CREATE INDEX idx_profile_metadata_profile_obis ON model_profile_metadata (profile_obis);

CREATE INDEX ix_obis_code_combined ON obis_mapping (obis_code_combined);

CREATE UNIQUE INDEX ux_model_obis_code_combined ON obis_mapping (model, obis_code_combined);

ALTER TABLE bands
    ADD CONSTRAINT bands_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE bands_version
    ADD CONSTRAINT bands_version_approved_by_fkey FOREIGN KEY (approve_by) REFERENCES users (id) ON DELETE NO ACTION;

ALTER TABLE bands_version
    ADD CONSTRAINT bands_version_band_id_fkey FOREIGN KEY (band_id) REFERENCES bands (id) ON DELETE NO ACTION;

ALTER TABLE bands_version
    ADD CONSTRAINT bands_version_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE NO ACTION;

ALTER TABLE bands_version
    ADD CONSTRAINT bands_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE credit_debit_adjustment
    ADD CONSTRAINT credit_debit_adjustment_liability_cause_id_fkey FOREIGN KEY (liability_cause_id) REFERENCES liability_cause (id) ON DELETE NO ACTION;

ALTER TABLE credit_debit_adjustment
    ADD CONSTRAINT credit_debit_adjustment_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE NO ACTION;

ALTER TABLE credit_debit_adjustment
    ADD CONSTRAINT credit_debit_adjustment_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE credit_debit_payment
    ADD CONSTRAINT credit_debit_payment_credit_debit_adj_id_fkey FOREIGN KEY (credit_debit_adj_id) REFERENCES credit_debit_adjustment (id) ON DELETE NO ACTION;

ALTER TABLE credit_debit_payment
    ADD CONSTRAINT credit_debit_payment_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE customers
    ADD CONSTRAINT customers_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE debt_percentage
    ADD CONSTRAINT debt_percentage_band_id_fkey FOREIGN KEY (band_id) REFERENCES bands (id) ON DELETE NO ACTION;

ALTER TABLE debt_percentage
    ADD CONSTRAINT debt_percentage_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE debt_percentage_version
    ADD CONSTRAINT debt_percentage_version_band_id_fkey FOREIGN KEY (band_id) REFERENCES bands (id) ON DELETE NO ACTION;

ALTER TABLE debt_percentage_version
    ADD CONSTRAINT debt_percentage_version_debt_percentage_id_fkey FOREIGN KEY (debt_percentage_id) REFERENCES debt_percentage (id) ON DELETE NO ACTION;

ALTER TABLE debt_percentage_version
    ADD CONSTRAINT debt_percentage_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE meters
    ADD CONSTRAINT fk_meter_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id) ON DELETE NO ACTION;

ALTER TABLE meters
    ADD CONSTRAINT fk_meter_manufacturer FOREIGN KEY (meter_manufacturer) REFERENCES manufacturers (id) ON DELETE NO ACTION;

ALTER TABLE qrtz_blob_triggers
    ADD CONSTRAINT fk_qrtz_blob_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;

ALTER TABLE qrtz_cron_triggers
    ADD CONSTRAINT fk_qrtz_cron_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;

ALTER TABLE qrtz_simple_triggers
    ADD CONSTRAINT fk_qrtz_simple_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;

ALTER TABLE qrtz_simprop_triggers
    ADD CONSTRAINT fk_qrtz_simprop_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;

ALTER TABLE qrtz_triggers
    ADD CONSTRAINT fk_qrtz_triggers_qrtz_job_details FOREIGN KEY (sched_name, job_name, job_group) REFERENCES qrtz_job_details (sched_name, job_name, job_group) ON DELETE NO ACTION;

ALTER TABLE meters_version
    ADD CONSTRAINT fk_tariff FOREIGN KEY (tariff) REFERENCES tariffs (id) ON DELETE NO ACTION;

ALTER TABLE group_permissions
    ADD CONSTRAINT group_permissions_group_id_fkey FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE NO ACTION;

ALTER TABLE group_permissions
    ADD CONSTRAINT group_permissions_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE group_permissions
    ADD CONSTRAINT group_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE NO ACTION;

ALTER TABLE groups
    ADD CONSTRAINT groups_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE liability_cause
    ADD CONSTRAINT liability_cause_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE liability_cause_version
    ADD CONSTRAINT liability_cause_version_liability_cause_id_fkey FOREIGN KEY (liability_cause_id) REFERENCES liability_cause (id) ON DELETE NO ACTION;

ALTER TABLE liability_cause_version
    ADD CONSTRAINT liability_cause_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE manufacturers
    ADD CONSTRAINT manufacturers_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE md_meters_info
    ADD CONSTRAINT md_meters_info_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE NO ACTION;

ALTER TABLE md_meters_info
    ADD CONSTRAINT md_meters_info_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE md_meters_info_version
    ADD CONSTRAINT md_meters_info_version_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters_version (id) ON DELETE NO ACTION;

ALTER TABLE md_meters_info_version
    ADD CONSTRAINT md_meters_info_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE meter_assign_locations
    ADD CONSTRAINT meter_assign_locations_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE NO ACTION;

ALTER TABLE meter_assign_locations
    ADD CONSTRAINT meter_assign_locations_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE meter_assign_locations_version
    ADD CONSTRAINT meter_assign_locations_version_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE NO ACTION;

ALTER TABLE meter_assign_locations_version
    ADD CONSTRAINT meter_assign_locations_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE meters
    ADD CONSTRAINT meters_node_id_fkey FOREIGN KEY (node_id) REFERENCES nodes (id) ON DELETE NO ACTION;

ALTER TABLE meters
    ADD CONSTRAINT meters_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE meters_version
    ADD CONSTRAINT meters_version_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customers (customer_id) ON DELETE NO ACTION;

ALTER TABLE meters_version
    ADD CONSTRAINT meters_version_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE NO ACTION;

ALTER TABLE meters_version
    ADD CONSTRAINT meters_version_meter_manufacturer_fkey FOREIGN KEY (meter_manufacturer) REFERENCES manufacturers (id) ON DELETE NO ACTION;

ALTER TABLE meters_version
    ADD CONSTRAINT meters_version_node_id_fkey FOREIGN KEY (node_id) REFERENCES nodes (id) ON DELETE NO ACTION;

ALTER TABLE meters_version
    ADD CONSTRAINT meters_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE modules
    ADD CONSTRAINT modules_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE nodes
    ADD CONSTRAINT nodes_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE nodes
    ADD CONSTRAINT nodes_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES nodes (id) ON DELETE NO ACTION;

ALTER TABLE payment_mode
    ADD CONSTRAINT payment_mode_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE NO ACTION;

ALTER TABLE payment_mode
    ADD CONSTRAINT payment_mode_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE payment_mode_version
    ADD CONSTRAINT payment_mode_version_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters_version (id) ON DELETE NO ACTION;

ALTER TABLE payment_mode_version
    ADD CONSTRAINT payment_mode_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE permissions
    ADD CONSTRAINT permissions_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE portal_roles
    ADD CONSTRAINT portal_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES portal_users (id) ON DELETE NO ACTION;

ALTER TABLE region_bhub_service_centers
    ADD CONSTRAINT regions_node_id_fkey FOREIGN KEY (node_id) REFERENCES nodes (id) ON DELETE NO ACTION;

ALTER TABLE region_bhub_service_centers
    ADD CONSTRAINT regions_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE smart_meter_info
    ADD CONSTRAINT smart_meter_info_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters (id) ON DELETE NO ACTION;

ALTER TABLE smart_meter_info
    ADD CONSTRAINT smart_meter_info_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE smart_meter_info_version
    ADD CONSTRAINT smart_meter_info_version_meter_id_fkey FOREIGN KEY (meter_id) REFERENCES meters_version (id) ON DELETE NO ACTION;

ALTER TABLE smart_meter_info_version
    ADD CONSTRAINT smart_meter_info_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE submodules
    ADD CONSTRAINT submodules_module_id_fkey FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE NO ACTION;

ALTER TABLE submodules
    ADD CONSTRAINT submodules_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE substation_trans_feeder_lines
    ADD CONSTRAINT substations_node_id_fkey FOREIGN KEY (node_id) REFERENCES nodes (id) ON DELETE NO ACTION;

ALTER TABLE substation_trans_feeder_lines
    ADD CONSTRAINT substations_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE tariffs
    ADD CONSTRAINT tariffs_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE tariffs_version
    ADD CONSTRAINT tariffs_version_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE NO ACTION;

ALTER TABLE tariffs_version
    ADD CONSTRAINT tariffs_version_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE NO ACTION;

ALTER TABLE tariffs_version
    ADD CONSTRAINT tariffs_version_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE tariffs_version
    ADD CONSTRAINT tariffs_version_t_id_fkey FOREIGN KEY (t_id) REFERENCES tariffs (id) ON DELETE NO ACTION;

ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_group_id_fkey FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE NO ACTION;

ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;

ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE NO ACTION;

ALTER TABLE users
    ADD CONSTRAINT users_node_id_fkey FOREIGN KEY (node_id) REFERENCES nodes (id) ON DELETE NO ACTION;

ALTER TABLE users
    ADD CONSTRAINT users_org_id_fkey FOREIGN KEY (org_id) REFERENCES organizations (id) ON DELETE NO ACTION;