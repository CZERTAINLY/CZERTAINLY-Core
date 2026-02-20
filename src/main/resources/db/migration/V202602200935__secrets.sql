CREATE TABLE vault_instance (
    uuid UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    description VARCHAR,
    connector_uuid UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (connector_uuid) REFERENCES connector(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE vault_profile (
    uuid UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    description VARCHAR NOT NULL,
    vault_instance_uuid UUID NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (vault_instance_uuid) REFERENCES vault_instance(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE secret (
    uuid UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    description VARCHAR,
    source_vault_profile_uuid UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    latest_version_uuid UUID NOT NULL,
    type VARCHAR NOT NULL,
    state VARCHAR NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (source_vault_profile_uuid) REFERENCES vault_profile(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE secret_version (
    uuid UUID PRIMARY KEY,
    secret_uuid UUID NOT NULL,
    version INT NOT NULL,
    fingerprint VARCHAR NOT NULL,
    vault_instance_uuid UUID NOT NULL,
    vault_version INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (secret_uuid) REFERENCES secret(uuid) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (vault_instance_uuid) REFERENCES vault_instance(uuid) ON UPDATE CASCADE ON DELETE SET NULL
);

ALTER TABLE secret
ADD CONSTRAINT fk_secret_latest_version
FOREIGN KEY (latest_version_uuid) REFERENCES secret_version(uuid) ON UPDATE CASCADE ON DELETE RESTRICT;