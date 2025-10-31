
CREATE TABLE meters_connection_detail (
    id       CHAR(36)                   DEFAULT gen_random_uuid() NOT NULL,
    meter_no VARCHAR(12)                UNIQUE                    NOT NULL,
    ip_address VARCHAR(255)                                       NOT NULL,
    connection_time TIMESTAMP WITHOUT TIME ZONE                   NOT NULL,
    heartbeat TIMESTAMP WITHOUT TIME ZONE                         NOT NULL,
    is_online BOOLEAN,
    created_at TIMESTAMP WITHOUT TIME ZONE                        NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE                        NOT NULL,
    CONSTRAINT meters_connection_detail_pk PRIMARY KEY (id)
);
