CREATE TABLE cbom
(
    uuid                  UUID PRIMARY KEY,
    created_at            TIMESTAMPTZ NOT NULL,
    serial_number         TEXT        NOT NULL,
    version               INT         NOT NULL,
    spec_version          TEXT        NOT NULL,
    timestamp             TIMESTAMPTZ NOT NULL,
    source                TEXT,
    algorithms_count      INT         NOT NULL,
    certificates_count    INT         NOT NULL,
    protocols_count       INT         NOT NULL,
    crypto_material_count INT         NOT NULL,
    total_assets_count    INT         NOT NULL,
    CONSTRAINT cbom_serial_version_unique UNIQUE (serial_number, version)
);
