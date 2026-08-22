-- Level-ladder configuration for CONTENT_SIGNING profiles. All four are nullable. Profile save is what makes the
-- first three mandatory for ILM-managed signing; a delegated profile carries none of them. document_size_cap is
-- never mandatory and is legal under either signing scheme.
--
-- No data step accompanies these columns. Content signing has never been used in the field, so no deployed
-- database holds a CONTENT_SIGNING version row that would need one.
ALTER TABLE "signing_profile_version"
    ADD COLUMN "signature_family"              VARCHAR NULL,
    ADD COLUMN "max_signature_level"           VARCHAR NULL,
    ADD COLUMN "timestamp_source_profile_uuid" UUID    NULL,
    ADD COLUMN "document_size_cap"             BIGINT  NULL;

-- The referenced profile must outlive the reference; RESTRICT makes deleting a referenced TSA profile an error
-- rather than a silently broken content-signing profile.
ALTER TABLE "signing_profile_version"
    ADD CONSTRAINT "fk_spv_timestamp_source_profile"
        FOREIGN KEY ("timestamp_source_profile_uuid") REFERENCES "signing_profile" ("uuid") ON DELETE RESTRICT;

-- Backs the new FK: without it every signing_profile delete sequential-scans signing_profile_version to
-- enforce the constraint. Mirrors the FK-column indexes the table already carries.
CREATE INDEX idx_spv_timestamp_source_profile_uuid
    ON "signing_profile_version" ("timestamp_source_profile_uuid");

-- The serials of the timestamp tokens an operation embedded, so a TIMESTAMPED signature joins to the signing
-- records of its timestamps. Kept out of "request_metadata_json" because that column is written only when the
-- profile's recordRequestMetadata toggle is on, which would make the trace optional.
ALTER TABLE "signing_record" ADD COLUMN "timestamp_token_serials" TEXT[];
ALTER TABLE "signing_record_outbox" ADD COLUMN "timestamp_token_serials" TEXT[];

-- Backs the join in both directions: a content-signing record is found by a serial it embedded, and a timestamp
-- record is found by the serial it was issued under.
CREATE INDEX idx_sr_timestamp_token_serials
    ON "signing_record" USING GIN ("timestamp_token_serials");
