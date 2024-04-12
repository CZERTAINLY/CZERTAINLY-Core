ALTER TABLE discovery_certificate ADD COLUMN meta JSONB;

CREATE TABLE rule_trigger_2_object (
    uuid UUID NOT NULL,
    resource VARCHAR NOT NULL,
    trigger_uuid UUID NOT NULL,
    object_uuid UUID NOT NULL,
    trigger_order INT,
    PRIMARY KEY (uuid)
)