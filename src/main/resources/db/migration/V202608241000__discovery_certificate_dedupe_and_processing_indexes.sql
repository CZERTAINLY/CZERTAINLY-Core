-- The connector's own key for a staged certificate, which the wire contract defines as what Core dedupes an
-- item by "across drains and retries". discovery_item has carried it since the v2 schema; this table, which
-- predates v2, had nowhere to put it, so v2 certificate dedupe was per-page and in memory.
--
-- Nullable because v1 discovery has no such key. Its provider uuid identifies a certificate rather than an
-- occurrence of one, so writing it here would hand the v1 flow a uniqueness guarantee no provider has ever
-- been asked to meet.
ALTER TABLE "discovery_certificate" ADD COLUMN "unique_ref" VARCHAR;

-- What makes the reference a dedupe key rather than a label: one reference may be staged once per run,
-- however many drains carry it. Partial, so v1's null rows sit outside the constraint entirely. One
-- certificate found on several hosts is several items with several references, so it stays several rows.
CREATE UNIQUE INDEX "uq_discovery_certificate_ref"
    ON "discovery_certificate" ("discovery_uuid", "unique_ref")
    WHERE "unique_ref" IS NOT NULL;

-- discovery_certificate has carried no index since it was created: the foreign key to discovery is a
-- constraint, and PostgreSQL does not index the referencing side, so every predicate below is a sequential
-- scan today. The v2 processing claim runs them on every tick.

-- The claim and its backlog counts. Partial because a row leaves this set permanently once it carries an
-- outcome, so the index shrinks as a run drains rather than growing with it -- the same shape as
-- idx_discovery_item_unprocessed. Column order follows the claim: the run is an equality, the content is both
-- the GROUP BY key and the IN-list of the follow-up read, and i_cre is what MIN() orders the groups by, kept
-- in the index so that minimum is read without touching the heap.
CREATE INDEX "idx_discovery_certificate_pending"
    ON "discovery_certificate" ("discovery_uuid", "certificate_content_id", "i_cre")
    WHERE "newly_discovered" = true AND "processed" = false AND "processed_error" IS NULL;

-- The end-of-run WARNING check. Tiny by construction: only rows that recorded a reason are in it, which on a
-- healthy run is none.
CREATE INDEX "idx_discovery_certificate_failed"
    ON "discovery_certificate" ("discovery_uuid")
    WHERE "processed_error" IS NOT NULL;
