-- Add accuracy column to time_quality_configuration table.
ALTER TABLE "time_quality_configuration"
    ADD COLUMN "accuracy" VARCHAR NOT NULL DEFAULT 1;
