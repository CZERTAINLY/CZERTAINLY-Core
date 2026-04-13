-- Add column to track whether a certificate carries the QcCompliance statement
-- (OID 0.4.0.19422.1.1) inside the QCStatements extension, as required by ETSI EN 319 421
-- for qualified electronic timestamps. NULL means the extension was absent or not yet parsed.
-- :TODO: This should be merged into a single file together with V202604011900__certificate_eku_critical.sql
ALTER TABLE "certificate"
    ADD COLUMN "qc_compliance" BOOLEAN;
