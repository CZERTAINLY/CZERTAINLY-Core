CREATE TABLE "approval_profile_relation"
(
    "uuid"                  UUID    NOT NULL,
    "approval_profile_uuid" UUID    NOT NULL,
    "resource_uuid"         UUID    NOT NULL,
    "resource"              VARCHAR NOT NULL,
    "action"                VARCHAR NULL DEFAULT NULL,
    PRIMARY KEY ("uuid"),
    FOREIGN KEY ("approval_profile_uuid") REFERENCES "approval_profile" ("uuid")
);


