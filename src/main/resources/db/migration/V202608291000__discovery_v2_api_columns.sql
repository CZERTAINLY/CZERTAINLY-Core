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

-- discovery_certificate is the only table in the schema whose audit columns are VARCHAR; every other one declares
-- them as a timestamp, and the entity has always mapped them as OffsetDateTime. Nothing noticed while no query
-- compared them against a timestamp -- the items listing is the first, and it fails at plan time as they stand.
--
-- Converted rather than cast at every call site, so the column finally matches the entity that maps it. Existing
-- values are ISO-8601 text written by Hibernate, so the cast is total; they are interpreted in the server's zone,
-- which is where they were written. Rewrites the table under an ACCESS EXCLUSIVE lock -- brief, but this is one of
-- the larger tables in a mature deployment, so it wants a maintenance window rather than a busy period.
ALTER TABLE "discovery_certificate"
    ALTER COLUMN "i_cre" TYPE TIMESTAMPTZ USING "i_cre"::timestamptz,
    ALTER COLUMN "i_upd" TYPE TIMESTAMPTZ USING "i_upd"::timestamptz;

-- Serves both the items listing and its count. Every existing discovery_certificate index is partial -- on
-- unique_ref IS NOT NULL, on the pending predicate, on processed_error IS NOT NULL -- and a v1 run's rows satisfy
-- none of them, so without this both sequentially scan one of the largest tables in a mature deployment. The
-- i_cre column is included because the listing orders the certificate branch by it.
--
-- Built after the conversion above, not before: an index on i_cre is rebuilt by any change to that column's type,
-- so creating it first would index the table twice.
CREATE INDEX "idx_discovery_certificate_run"
    ON "discovery_certificate" ("discovery_uuid", "i_cre", "uuid");

-- The run's interface reference is declared as a real foreign key, the way secrets and
-- authority_instance_reference declare theirs. RESTRICT rather than SET NULL: connector_interface_uuid is also what
-- says a run was driven by v2 at all -- NULL means a legacy v1 run -- so nulling it to satisfy a cascade would
-- reclassify finished v2 runs as v1. ConnectorServiceImpl.removeConnectorAssociations clears the reference before a
-- connector is deleted, exactly as it already does for token and vault instances, so the cascade below it still
-- proceeds.
--
-- Any reference already dangling is cleared first. Until now the column carried no constraint, so a connector
-- force-deleted before this migration left its runs pointing at interfaces that cascade-deleted with it; the
-- constraint cannot be added while those rows exist.
UPDATE "discovery"
SET "connector_interface_uuid" = NULL
WHERE "connector_interface_uuid" IS NOT NULL
  AND NOT EXISTS (SELECT 1
                  FROM "connector_interface" ci
                  WHERE ci."uuid" = "discovery"."connector_interface_uuid");

-- No index on the column, matching vault_instance and authority_instance_reference: the association is loaded by
-- connector_interface's own primary key, so a run detail never scans by it, and the only reader is the constraint
-- check when an interface is deleted -- which happens on connector deletion, after the release above has already
-- emptied the column.
ALTER TABLE "discovery"
    ADD CONSTRAINT "fk_discovery_connector_interface"
        FOREIGN KEY ("connector_interface_uuid") REFERENCES "connector_interface" ("uuid")
        ON UPDATE CASCADE ON DELETE RESTRICT;
