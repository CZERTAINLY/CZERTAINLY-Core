ALTER TABLE ra_profile ADD COLUMN validation_enabled BOOLEAN;
ALTER TABLE ra_profile ADD COLUMN validation_frequency INTEGER;
ALTER TABLE ra_profile ADD COLUMN expiring_threshold INTEGER;

UPDATE ra_profile SET validation_enabled = TRUE;
ALTER TABLE ra_profile ALTER COLUMN validation_enabled SET NOT NULL;
