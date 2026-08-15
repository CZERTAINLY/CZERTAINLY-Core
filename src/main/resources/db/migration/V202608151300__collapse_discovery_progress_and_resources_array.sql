-- Amendments to the young v2 schema before anything writes these columns. A fresh migration rather than an
-- edit: V202608141100 is merged, and editing an applied migration breaks Flyway checksum validation.
--
-- progress collapses into one jsonb snapshot: the connector's latest report is written and read as a whole,
-- and a single value makes a torn snapshot — fields mixed from two reports — unrepresentable under the two
-- concurrent writers (status poll, progress event). resources becomes TEXT[], the platform's shape for a
-- flat enum list (connector_interface.features). Both columns are unwritten so far, hence drop-and-add.
ALTER TABLE "discovery"
    DROP COLUMN "progress_processed",
    DROP COLUMN "progress_total_estimate",
    DROP COLUMN "progress_by_resource",
    DROP COLUMN "progress_phase",
    ADD COLUMN "progress" JSONB,
    DROP COLUMN "resources",
    ADD COLUMN "resources" TEXT[];
