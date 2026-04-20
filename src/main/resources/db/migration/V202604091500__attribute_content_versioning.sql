-- Add version dimension to attribute content object mappings.
-- NULL means unversioned (all existing rows, all non-signing resources).
-- Versioned resources (e.g. signing profiles) will write explicit integer versions.
ALTER TABLE attribute_content_2_object
    ADD COLUMN object_version INTEGER NULL;

-- Composite index to support efficient versioned lookups:
--   SELECT ... WHERE object_type = ? AND object_uuid = ? AND object_version = ?
CREATE INDEX idx_aco_type_uuid_version
    ON attribute_content_2_object (object_type, object_uuid, object_version);
