-- Columns the discovery v2 API surface needs. All nullable and all written by the v2 path only, so a v1 run keeps
-- exactly the shape it has today and nothing is backfilled.

-- Whether the connector said this run can be stopped and resumed, as declared at initiate and refreshed by each
-- resume. NULL for a v1 run, which cannot be stopped at all; the detail publishes that as false.
ALTER TABLE "discovery" ADD COLUMN "stoppable" BOOLEAN;

-- Numbered and timestamped as every other resource is, so one run's certificates and keys can be ordered against
-- each other. NULL on a v1 row, whose provider numbered nothing: the listing synthesizes both at read time rather
-- than backfilling values that never existed. Metadata-only ALTERs.
ALTER TABLE "discovery_certificate" ADD COLUMN "sequence" BIGINT;
ALTER TABLE "discovery_certificate" ADD COLUMN "discovered_at" TIMESTAMPTZ;

-- The scheduled job execution that started the run. A v1 run carries it through one synchronous call chain; a v2
-- run ends later in a tick worker with no memory of who asked for it, so without this the scheduler is never told
-- and the job hangs open. The execution alone -- scheduled_job_history already points at its job.
ALTER TABLE "discovery" ADD COLUMN "scheduled_job_history_uuid" UUID;

-- The only audit columns in the schema still declared VARCHAR, though the entity has always mapped them as
-- OffsetDateTime. The items listing is the first query to compare them against a timestamp and fails at plan time
-- as they stand. Existing values are ISO-8601 written by Hibernate and are read in the server's zone, where they
-- were written, so the cast is total. Rewrites the table under ACCESS EXCLUSIVE: brief, but one of the larger
-- tables in a mature deployment, so it wants a maintenance window.
ALTER TABLE "discovery_certificate"
    ALTER COLUMN "i_cre" TYPE TIMESTAMPTZ USING "i_cre"::timestamptz,
    ALTER COLUMN "i_upd" TYPE TIMESTAMPTZ USING "i_upd"::timestamptz;

-- Serves the items listing and its count. Every existing discovery_certificate index is partial and a v1 run's
-- rows satisfy none of them, so without this both sequentially scan the table; i_cre is included because the
-- listing orders the certificate branch by it. Built after the conversion above, which would otherwise rebuild it.
CREATE INDEX "idx_discovery_certificate_run"
    ON "discovery_certificate" ("discovery_uuid", "i_cre", "uuid");

-- A real foreign key, as vault_instance and authority_instance_reference declare theirs. RESTRICT rather than SET
-- NULL: this column is also what says a run was v2 -- NULL means a legacy v1 run -- so nulling it for a cascade
-- would reclassify finished v2 runs. ConnectorServiceImpl.removeConnectorAssociations releases the runs first, so
-- the interfaces still cascade away. References already dangling are cleared here, since the column carried no
-- constraint until now and the constraint cannot be added while they exist.
UPDATE "discovery"
SET "connector_interface_uuid" = NULL
WHERE "connector_interface_uuid" IS NOT NULL
  AND NOT EXISTS (SELECT 1
                  FROM "connector_interface" ci
                  WHERE ci."uuid" = "discovery"."connector_interface_uuid");

-- No index, as on vault_instance and authority_instance_reference: the association loads by connector_interface's
-- own primary key, so nothing scans discovery by this column except the constraint check on interface deletion.
ALTER TABLE "discovery"
    ADD CONSTRAINT "fk_discovery_connector_interface"
        FOREIGN KEY ("connector_interface_uuid") REFERENCES "connector_interface" ("uuid")
        ON UPDATE CASCADE ON DELETE RESTRICT;
