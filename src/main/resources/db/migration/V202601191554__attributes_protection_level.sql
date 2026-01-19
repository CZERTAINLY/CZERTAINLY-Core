ALTER TABLE attribute_definition ADD COLUMN protection_level VARCHAR;

UPDATE attribute_definition SET protection_level = 'NO_PROTECTION' WHERE type IN ('CUSTOM', 'DATA', 'META');
