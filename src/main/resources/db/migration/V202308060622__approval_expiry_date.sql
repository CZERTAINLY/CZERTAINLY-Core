ALTER TABLE approval ADD COLUMN expiry_at TIMESTAMP NULL;

UPDATE approval SET expiry_at = created_at + INTERVAL '1 hour' * (SELECT ap.expiry FROM approval_profile_version AS ap WHERE ap.uuid = approval_profile_version_uuid);