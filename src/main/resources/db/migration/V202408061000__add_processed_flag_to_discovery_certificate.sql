ALTER TABLE "discovery_certificate"
    ADD "processed" BOOLEAN NOT NULL DEFAULT false,
    ADD "processed_error" TEXT;
