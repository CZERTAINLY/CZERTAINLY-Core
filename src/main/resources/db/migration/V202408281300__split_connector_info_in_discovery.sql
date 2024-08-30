ALTER TABLE "discovery_history"
    ADD "connector_status" VARCHAR,
    ADD "connector_total_certificates_discovered" INTEGER;

UPDATE "discovery_history" SET "connector_status" = "status";
UPDATE "discovery_history" SET "connector_total_certificates_discovered" = "total_certificates_discovered";

ALTER TABLE "discovery_history" ALTER COLUMN "connector_status" SET NOT NULL;

