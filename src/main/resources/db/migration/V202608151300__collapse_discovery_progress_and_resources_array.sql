-- Amendments to the young v2 schema: the progress_* columns collapse into a single jsonb snapshot and
-- resources becomes TEXT[]; the rationale lives on the entity fields. A fresh migration rather than an edit
-- because V202608141100 is merged, and both columns are unwritten so far — hence drop-and-add.
ALTER TABLE "discovery"
    DROP COLUMN "progress_processed",
    DROP COLUMN "progress_total_estimate",
    DROP COLUMN "progress_by_resource",
    DROP COLUMN "progress_phase",
    ADD COLUMN "progress" JSONB,
    DROP COLUMN "resources",
    ADD COLUMN "resources" TEXT[];
