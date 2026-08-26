-- Run messages move off the run row: from a JSONB array of strings rewritten whole under the run's write lock
-- to one row per distinct problem, appended by upsert and aggregated by occurrence count.

CREATE TABLE "discovery_message" (
    -- Ordering only, never exposed; a timestamp cannot order these (see DiscoveryMessage.id).
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "discovery_uuid" UUID NOT NULL,
    "severity" VARCHAR NOT NULL,
    "code" VARCHAR NOT NULL,           -- kind of problem: connector-supplied for connector errors, else Core's own
    "message" VARCHAR NOT NULL,
    -- Hashed to keep the unique index entry inside the btree limit whatever the message length.
    "message_hash" VARCHAR GENERATED ALWAYS AS (md5("message")) STORED,
    "occurrences" BIGINT NOT NULL DEFAULT 1,
    "first_seen_at" TIMESTAMPTZ NOT NULL,
    "last_seen_at" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "discovery_message_to_discovery_key" FOREIGN KEY ("discovery_uuid")
        REFERENCES "discovery" ("uuid") ON DELETE CASCADE,
    CONSTRAINT "uq_discovery_message" UNIQUE ("discovery_uuid", "code", "message_hash")
);

-- Serves the run's listing, and the per-run bound the writer checks before every new row.
CREATE INDEX "idx_discovery_message_run" ON "discovery_message" ("discovery_uuid", "id");

ALTER TABLE "discovery" DROP COLUMN "run_messages";
