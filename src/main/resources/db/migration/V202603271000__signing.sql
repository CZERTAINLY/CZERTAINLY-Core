-- ── 1. time_quality_configuration (no FK dependencies) ─────────────────
CREATE TABLE "time_quality_configuration" (
    "uuid"                        UUID         NOT NULL,
    "name"                        VARCHAR      NOT NULL,
    "description"                 TEXT,
    "accuracy"                    INTERVAL,
    "ntp_servers"                 TEXT[]       NOT NULL,
    "ntp_check_interval"          INTERVAL     NOT NULL DEFAULT INTERVAL '30 seconds',
    "ntp_samples_per_server"      INTEGER      NOT NULL DEFAULT 4,
    "ntp_check_timeout"           INTERVAL     NOT NULL DEFAULT INTERVAL '5 seconds',
    "ntp_servers_min_reachable"   INTEGER      NOT NULL DEFAULT 1,
    "max_clock_drift"             INTERVAL     NOT NULL DEFAULT INTERVAL '1 second',
    "leap_second_guard"           BOOLEAN      NOT NULL DEFAULT TRUE,
    "i_author"                    VARCHAR,
    "i_cre"                       TIMESTAMP    DEFAULT NOW(),
    "i_upd"                       TIMESTAMP    DEFAULT NOW(),
    PRIMARY KEY ("uuid"),
    UNIQUE ("name")
);

-- ── 2. signing_profile (protocol config FKs added below after those tables exist)
CREATE TABLE "signing_profile" (
    "uuid"                                  UUID         NOT NULL,
    "name"                                  VARCHAR      NOT NULL,
    "description"                           TEXT,
    "enabled"                               BOOLEAN      NOT NULL DEFAULT FALSE,
    "signing_scheme"                        VARCHAR      NOT NULL, -- MANAGED | DELEGATED
    "managed_signing_type"                  VARCHAR,               -- STATIC_KEY | ONE_TIME_KEY | NULL
    "workflow_type"                         VARCHAR      NOT NULL, -- CONTENT_SIGNING | RAW_SIGNING | TIMESTAMPING
    "latest_version"                        INTEGER      NOT NULL DEFAULT 1,
    -- scheme columns (type-conditional, nullable)
    "token_profile_uuid"                    UUID,
    "certificate_uuid"                      UUID,
    "ra_profile_uuid"                       UUID,
    "csr_template_uuid"                     UUID, -- no FK, entity TBD
    "delegated_signer_connector_uuid"       UUID,
    -- workflow columns (type-conditional, nullable)
    "signature_formatter_connector_uuid"    UUID,
    "qualified_timestamp"                   BOOLEAN,
    "time_quality_config_uuid"              UUID         REFERENCES "time_quality_configuration" ("uuid") ON DELETE RESTRICT,
    "default_policy_id"                     VARCHAR,
    "allowed_policy_ids"                    TEXT[],
    "allowed_digest_algorithms"             TEXT[],
    -- protocol activation (nullable, FKs added below)
    "ilm_signing_protocol_configuration_uuid" UUID,
    "tsp_configuration_uuid"                UUID,
    "i_author"                              VARCHAR,
    "i_cre"                                 TIMESTAMP    DEFAULT NOW(),
    "i_upd"                                 TIMESTAMP    DEFAULT NOW(),
    PRIMARY KEY ("uuid"),
    UNIQUE ("name")
);

-- ── 3. ilm_signing_protocol_configuration (FK to signing_profile added below)
CREATE TABLE "ilm_signing_protocol_configuration" (
    "uuid"                          UUID       NOT NULL,
    "name"                          VARCHAR    NOT NULL,
    "description"                   TEXT,
    "enabled"                       BOOLEAN    NOT NULL DEFAULT FALSE,
    "default_signing_profile_uuid"  UUID,
    "i_author"                      VARCHAR,
    "i_cre"                         TIMESTAMP  DEFAULT NOW(),
    "i_upd"                         TIMESTAMP  DEFAULT NOW(),
    PRIMARY KEY ("uuid"),
    UNIQUE ("name")
);

-- ── 4. tsp_configuration
CREATE TABLE "tsp_configuration" (
    "uuid"                          UUID       NOT NULL,
    "name"                          VARCHAR    NOT NULL,
    "description"                   TEXT,
    "enabled"                       BOOLEAN    NOT NULL DEFAULT FALSE,
    "default_signing_profile_uuid"  UUID,
    "i_author"                      VARCHAR,
    "i_cre"                         TIMESTAMP  DEFAULT NOW(),
    "i_upd"                         TIMESTAMP  DEFAULT NOW(),
    PRIMARY KEY ("uuid"),
    UNIQUE ("name")
);

-- ── 5. signing_profile_version: immutable version snapshots
CREATE TABLE "signing_profile_version" (
    "uuid"                  UUID         NOT NULL,
    "signing_profile_uuid"  UUID         NOT NULL,
    "version"               INTEGER      NOT NULL,
    "scheme_snapshot"       JSONB        NOT NULL, -- serialised flat columns at version-bump
    "workflow_snapshot"     JSONB        NOT NULL, -- serialised flat columns at version-bump
    "created_at"            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "i_author"              VARCHAR,
    "i_cre"                 TIMESTAMP  DEFAULT NOW(),
    "i_upd"                 TIMESTAMP  DEFAULT NOW(),
    PRIMARY KEY ("uuid"),
    UNIQUE ("signing_profile_uuid", "version"),
    FOREIGN KEY ("signing_profile_uuid") REFERENCES "signing_profile" ("uuid") ON DELETE CASCADE
);

-- ── 6. digital_signature: records of signing operations
CREATE TABLE "digital_signature" (
    "uuid"                    UUID         NOT NULL,
    "name"                    VARCHAR,
    "signing_profile_uuid"    UUID,  -- nullable so we can delete signing profiles but keep signatures
    "signing_profile_version" INTEGER      NOT NULL,
    "signing_time"            TIMESTAMPTZ  NOT NULL,
    "created_at"              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "signature_value"         BYTEA,
    "i_author"                VARCHAR,
    "i_cre"                   TIMESTAMP  DEFAULT NOW(),
    "i_upd"                   TIMESTAMP  DEFAULT NOW(),
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("signing_profile_uuid") REFERENCES "signing_profile" ("uuid") ON DELETE SET NULL
);

CREATE INDEX idx_ds_profile_version ON "digital_signature" ("signing_profile_uuid", "signing_profile_version");


-- ── 5. Circular FK resolution: signing_profile ↔ ilm/tsp ──────────────────────
-- signing_profile → ilm/tsp (SET NULL: activations auto-clear if config deleted;
-- app layer prevents this via dependency check, but DB stays consistent)
ALTER TABLE "signing_profile"
    ADD CONSTRAINT fk_sp_ilm_config
        FOREIGN KEY ("ilm_signing_protocol_configuration_uuid")
            REFERENCES "ilm_signing_protocol_configuration" ("uuid") ON DELETE SET NULL;

ALTER TABLE "signing_profile"
    ADD CONSTRAINT fk_sp_tsp_config
        FOREIGN KEY ("tsp_configuration_uuid")
            REFERENCES "tsp_configuration" ("uuid") ON DELETE SET NULL;

-- ilm/tsp → signing_profile (RESTRICT: a profile used as default cannot be deleted)
ALTER TABLE "ilm_signing_protocol_configuration"
    ADD CONSTRAINT fk_ilm_default_sp
        FOREIGN KEY ("default_signing_profile_uuid")
            REFERENCES "signing_profile" ("uuid") ON DELETE RESTRICT;

ALTER TABLE "tsp_configuration"
    ADD CONSTRAINT fk_tsp_default_sp
        FOREIGN KEY ("default_signing_profile_uuid")
            REFERENCES "signing_profile" ("uuid") ON DELETE RESTRICT;
