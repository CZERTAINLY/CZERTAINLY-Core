ALTER TABLE attribute_definition ADD COLUMN protection_level VARCHAR;
ALTER TABLE attribute_definition ADD COLUMN encrypted_data VARCHAR[];
ALTER TABLE attribute_content_item ADD COLUMN encrypted_data VARCHAR;


UPDATE attribute_definition SET protection_level = 'NONE' WHERE type IN ('CUSTOM', 'DATA', 'META');
