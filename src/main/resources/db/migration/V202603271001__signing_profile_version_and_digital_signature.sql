-- signing_profile_version: immutable version snapshots
CREATE TABLE "signing_profile_version" (
    "uuid"                  UUID         NOT NULL,
    "signing_profile_uuid"  UUID         NOT NULL,
    "version"               INTEGER      NOT NULL,
    "scheme_snapshot"       JSONB        NOT NULL, -- serialised flat columns at version-bump
    "workflow_snapshot"     JSONB        NOT NULL, -- serialised flat columns at version-bump
    "created_at"            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY ("uuid"),
    UNIQUE ("signing_profile_uuid", "version"),
    FOREIGN KEY ("signing_profile_uuid") REFERENCES "signing_profile" ("uuid") ON DELETE CASCADE
);

-- digital_signature: records of signing operations
CREATE TABLE "digital_signature" (
    "uuid"                    UUID         NOT NULL,
    "name"                    VARCHAR,
    "signing_profile_uuid"    UUID,  -- nullable so we can delete signing profiles but keep signatures
    "signing_profile_version" INTEGER      NOT NULL,
    "signing_time"            TIMESTAMPTZ  NOT NULL,
    "created_at"              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    "signature_value"         BYTEA,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("signing_profile_uuid") REFERENCES "signing_profile" ("uuid") ON DELETE SET NULL
);

CREATE INDEX idx_ds_profile_version ON "digital_signature" ("signing_profile_uuid", "signing_profile_version");
