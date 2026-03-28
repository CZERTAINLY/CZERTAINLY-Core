ALTER TABLE notification_profile
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;

ALTER TABLE notification_profile_version
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;
