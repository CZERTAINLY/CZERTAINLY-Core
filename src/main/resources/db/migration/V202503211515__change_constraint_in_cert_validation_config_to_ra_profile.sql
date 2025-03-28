ALTER TABLE ra_profile ALTER COLUMN validation_enabled DROP NOT NULL;
UPDATE ra_profile SET validation_enabled = NULL;
