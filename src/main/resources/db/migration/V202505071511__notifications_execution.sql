ALTER TABLE execution_item
    ADD COLUMN notification_profile_uuid UUID NULL DEFAULT NULL,
    ALTER COLUMN field_source DROP NOT NULL,
    ALTER COLUMN field_identifier DROP NOT NULL,
    ADD CONSTRAINT fk_execution_item_notification_profile FOREIGN KEY (notification_profile_uuid) REFERENCES notification_profile(uuid) ON UPDATE CASCADE ON DELETE RESTRICT;

CREATE TABLE "pending_notification"
(
    "uuid"                      UUID        NOT NULL,
    "notification_profile_uuid" UUID        NOT NULL,
    "version"                   INTEGER     NOT NULL,
    "resource"                  TEXT        NOT NULL,
    "object_uuid"               UUID        NOT NULL,
    "event"                     TEXT        NULL DEFAULT NULL,
    "last_sent_at"              TIMESTAMP   NOT NULL,
    "repetitions"               INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("notification_profile_uuid") REFERENCES "notification_profile" ("uuid") ON UPDATE CASCADE ON DELETE CASCADE
);