CREATE TABLE "approval_profile"
(
    "uuid"     UUID    NOT NULL,
    "name"     TEXT    NOT NULL,
    "enabled"  BOOLEAN NOT NULL,
    "i_author" VARCHAR NULL DEFAULT NULL,
    "i_cre"    DATE    NULL DEFAULT NULL,
    "i_upd"    DATE    NULL DEFAULT NULL,
    PRIMARY KEY ("uuid")
);

CREATE TABLE "approval_profile_version"
(
    "uuid"                  UUID    NOT NULL,
    "approval_profile_uuid" UUID    NOT NULL,
    "version"               INTEGER NOT NULL,
    "description"           TEXT    NULL,
    "expiry"                INTEGER NULL,
    "i_author"              VARCHAR NULL DEFAULT NULL,
    "i_cre"                 DATE    NULL DEFAULT NULL,
    "i_upd"                 DATE    NULL DEFAULT NULL,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("approval_profile_uuid") REFERENCES "approval_profile" ("uuid")
);

CREATE TABLE "approval"
(
    "uuid"                          UUID      NOT NULL,
    "approval_profile_version_uuid" UUID      NOT NULL,
    "creator_uuid"                  UUID      NOT NULL,
    "object_uuid"                   UUID      NOT NULL,
    "resource"                      TEXT      NOT NULL,
    "action"                        TEXT      NOT NULL,
    "status"                        TEXT      NOT NULL,
    "created_at"                    TIMESTAMP NOT NULL,
    "closed_at"                     TIMESTAMP NULL,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("approval_profile_version_uuid") REFERENCES "approval_profile_version" ("uuid")
);

CREATE TABLE "approval_step"
(
    "uuid"                          UUID    NOT NULL,
    "approval_profile_version_uuid" UUID    NOT NULL,
    "user_uuid"                     UUID    NULL,
    "role_uuid"                     UUID    NULL,
    "group_uuid"                    UUID    NULL,
    "description"                   TEXT    NULL,
    "order_id"                      INTEGER NOT NULL,
    "required_approvals"            INTEGER NULL,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("approval_profile_version_uuid") REFERENCES "approval_profile_version" ("uuid"),
    FOREIGN KEY ("group_uuid") REFERENCES "group" ("uuid")
);

CREATE TABLE "approval_recipient"
(
    "uuid"               UUID      NOT NULL,
    "user_uuid"          UUID      NULL,
    "approval_step_uuid" UUID      NOT NULL,
    "approval_uuid"      UUID      NOT NULL,
    "status"             TEXT      NOT NULL,
    "comment"            TEXT      NULL,
    "created_at"         TIMESTAMP NOT NULL,
    "closed_at"          TIMESTAMP NULL,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("approval_step_uuid") REFERENCES approval_step ("uuid"),
    FOREIGN KEY ("approval_uuid") REFERENCES approval ("uuid")
);

