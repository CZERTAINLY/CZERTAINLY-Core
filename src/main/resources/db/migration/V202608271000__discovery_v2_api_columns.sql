-- Columns the discovery v2 API surface needs. All nullable and all written by the v2 path only, so a v1 run keeps
-- exactly the shape it has today and nothing is backfilled.

-- Whether the connector said this run can be stopped and resumed, as declared at initiate and refreshed by each
-- resume. NULL for a v1 run, which cannot be stopped at all; the detail publishes that as false.
ALTER TABLE "discovery" ADD COLUMN "stoppable" BOOLEAN;

-- The v2 ingest path numbers and timestamps a staged certificate the same way it does every other resource, so one
-- run's certificates and keys can be ordered against each other by the items listing.
--
-- NULL on a v1 row, whose provider numbered nothing: the listing synthesizes both at read time — the number from
-- staging order, the timestamp from the row's creation — rather than backfilling values that never existed.
-- Metadata-only ALTERs.
ALTER TABLE "discovery_certificate" ADD COLUMN "sequence" BIGINT;
ALTER TABLE "discovery_certificate" ADD COLUMN "discovered_at" TIMESTAMPTZ;

-- The scheduled job execution that started the run, if any. A v1 run carries this through one synchronous call
-- chain and never needs it stored; a v2 run ends much later, in a tick worker that has no memory of who asked for
-- it, so without this the scheduler is never told the run finished and the job hangs open.
--
-- The execution alone: scheduled_job_history already carries scheduled_job_uuid, so the job it belongs to is one
-- read away rather than a second copy here. Neither the job nor its name is stored.
ALTER TABLE "discovery" ADD COLUMN "scheduled_job_history_uuid" UUID;
