-- Migration for Connector v2 schema

-- Add new column to connector table
ALTER TABLE connector
    ADD COLUMN version TEXT NULL;

-- Set default version to 'v1' for existing records
UPDATE connector SET version = 'V1';
ALTER TABLE connector
    ALTER COLUMN version SET NOT NULL;

-- Add unique constraint on (url, version) to prevent duplicate connectors
CREATE UNIQUE INDEX IF NOT EXISTS uq_connector_url_version ON connector(url, version);

-- Create connector_interface table for connectors v2 interfaces
CREATE TABLE IF NOT EXISTS connector_interface (
    uuid UUID NOT NULL PRIMARY KEY,
    connector_uuid UUID NOT NULL,
    interface TEXT NOT NULL,
    version TEXT NOT NULL,
    features TEXT[] NULL,
    CONSTRAINT fk_connector_interface_connector FOREIGN KEY (connector_uuid) REFERENCES connector(uuid) ON DELETE CASCADE,
    CONSTRAINT uq_connector_interface UNIQUE (connector_uuid, interface, version)
);

-- Add index for foreign key lookups
CREATE INDEX IF NOT EXISTS idx_connector_interface_connector_uuid ON connector_interface(connector_uuid);
