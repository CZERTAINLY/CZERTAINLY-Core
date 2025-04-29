-- Trigger

ALTER TABLE trigger
    DROP COLUMN event_resource,
    ALTER COLUMN type DROP NOT NULL;

-- Trigger association

ALTER TABLE trigger_association
    ADD COLUMN event TEXT,
    ADD COLUMN override BOOLEAN NOT NULL DEFAULT FALSE,
    ALTER COLUMN resource DROP NOT NULL,
    ALTER COLUMN object_uuid DROP NOT NULL;

UPDATE trigger_association SET event = 'CERTIFICATE_DISCOVERED';

-- Trigger history

ALTER TABLE trigger_history
    ADD COLUMN trigger_association_uuid UUID NULL,
    ADD COLUMN triggered_by UUID NULL;

ALTER TABLE trigger_history ADD CONSTRAINT trigger_history_to_trigger_association_FK
    FOREIGN KEY (trigger_association_uuid) REFERENCES trigger_association(uuid) ON DELETE CASCADE;

UPDATE trigger_history SET trigger_association_uuid = (SELECT ta.uuid FROM trigger_association AS ta WHERE ta.trigger_uuid = trigger_history.trigger_uuid AND ta.object_uuid = trigger_history.trigger_association_object_uuid);
ALTER TABLE trigger_history DROP COLUMN trigger_association_object_uuid;