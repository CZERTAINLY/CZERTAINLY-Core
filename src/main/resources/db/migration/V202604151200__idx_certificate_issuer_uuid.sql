-- Index to support recursive CTE chain traversal via issuer_certificate_uuid.
CREATE INDEX IF NOT EXISTS idx_certificate_issuer_certificate_uuid
    ON certificate (issuer_certificate_uuid)
    WHERE issuer_certificate_uuid IS NOT NULL;
