CREATE TABLE "notification_profile"
(
    "uuid"          UUID        NOT NULL,
    "name"          TEXT        NOT NULL,
    "description"   TEXT        NULL DEFAULT NULL,
    "version_lock"  INTEGER     NOT NULL,
    "created_at"    TIMESTAMP   NOT NULL,
    PRIMARY KEY ("uuid")
);

CREATE TABLE "notification_profile_version"
(
    "uuid"                              UUID        NOT NULL,
    "notification_profile_uuid"         UUID        NOT NULL,
    "version"                           INTEGER     NOT NULL,
    "recipient_type"                    TEXT        NOT NULL,
    "recipient_uuid"                    UUID        NULL,
    "notification_instance_ref_uuid"    UUID        NULL DEFAULT NULL,
    "internal_notification"             BOOLEAN     NOT NULL,
    "frequency"                         INTERVAL    NULL DEFAULT NULL,
    "repetitions"                       INTEGER     NULL DEFAULT NULL,
    "created_at"                        TIMESTAMP   NOT NULL,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("notification_profile_uuid") REFERENCES "notification_profile" ("uuid") ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY ("notification_instance_ref_uuid") REFERENCES "notification_instance_reference" ("uuid") ON UPDATE CASCADE ON DELETE RESTRICT
);
