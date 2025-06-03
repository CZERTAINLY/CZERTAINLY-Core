ALTER TABLE notification_profile_version
    ADD COLUMN recipient_uuids UUID ARRAY NULL DEFAULT NULL;

UPDATE notification_profile_version SET recipient_uuids = ARRAY[recipient_uuid] WHERE recipient_uuid IS NOT NULL;

ALTER TABLE notification_profile_version
    DROP COLUMN recipient_uuid;