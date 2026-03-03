CREATE TABLE vault_instance (
    uuid UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    description VARCHAR,
    connector_uuid UUID,
    connector_interface_uuid UUID,
    i_cre TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    i_upd TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    i_author VARCHAR,
    FOREIGN KEY (connector_uuid) REFERENCES connector(uuid) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (connector_interface_uuid) REFERENCES connector_interface(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE vault_profile (
    uuid UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    description VARCHAR,
    vault_instance_uuid UUID NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    i_cre TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    i_upd TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    i_author VARCHAR,
    FOREIGN KEY (vault_instance_uuid) REFERENCES vault_instance(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE secret (
    uuid UUID PRIMARY KEY,
    name VARCHAR NOT NULL,
    description VARCHAR,
    source_vault_profile_uuid UUID NOT NULL,
    i_cre TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    latest_version_uuid UUID,
    type VARCHAR NOT NULL,
    state VARCHAR NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    i_upd TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    i_author VARCHAR,
    FOREIGN KEY (source_vault_profile_uuid) REFERENCES vault_profile(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE secret_version (
    uuid UUID PRIMARY KEY,
    secret_uuid UUID NOT NULL,
    version INT NOT NULL,
    fingerprint VARCHAR NOT NULL,
    vault_instance_uuid UUID NOT NULL,
    vault_version VARCHAR,
    i_cre TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (secret_uuid) REFERENCES secret(uuid) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (vault_instance_uuid) REFERENCES vault_instance(uuid) ON UPDATE CASCADE ON DELETE RESTRICT
);

ALTER TABLE secret
ADD CONSTRAINT fk_secret_latest_version
FOREIGN KEY (latest_version_uuid) REFERENCES secret_version(uuid) ON UPDATE CASCADE ON DELETE RESTRICT;

CREATE TABLE secret_2_sync_vault_profile (
    secret_uuid UUID NOT NULL,
    vault_profile_uuid UUID NOT NULL,
    secret_attributes jsonb,
    PRIMARY KEY (secret_uuid, vault_profile_uuid),
    FOREIGN KEY (secret_uuid) REFERENCES secret(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (vault_profile_uuid) REFERENCES vault_profile(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);
