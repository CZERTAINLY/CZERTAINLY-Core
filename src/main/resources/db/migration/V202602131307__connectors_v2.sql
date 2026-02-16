-- Migration for Connector v2 schema

-- Add new columns to connector table if needed
ALTER TABLE connector
    ADD COLUMN version TEXT NULL;

-- Set default version to 'v1' for existing records
UPDATE connector SET version = 'v1';
ALTER TABLE connector
    ALTER COLUMN version TEXT NOT NULL;

-- Create connector_function_group table for v2
CREATE TABLE IF NOT EXISTS connector_interface (
    uuid UUID NOT NULL PRIMARY KEY,
    connector_uuid UUID NOT NULL,
    interface TEXT NOT NULL,
    version TEXT NOT NULL,
    features TEXT[] NOT NULL,
    CONSTRAINT fk_connector_interface_connector FOREIGN KEY (connector_uuid) REFERENCES connector(uuid) ON DELETE CASCADE
);