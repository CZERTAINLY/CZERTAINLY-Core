-- Add column to track whether the Extended Key Usage extension is marked critical,
-- as required by RFC 3161 for TSA certificates.
ALTER TABLE "certificate"
    ADD COLUMN "extended_key_usage_critical" BOOLEAN;
