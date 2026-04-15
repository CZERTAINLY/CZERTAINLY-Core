-- QC statement fields parsed from the QCStatements extension (OID 1.3.6.1.5.5.7.1.3)
-- per ETSI EN 319 412-5. NULL means extension was absent or certificate not yet re-parsed.
-- :TODO: This should be merged into a single file together with V202604011900__certificate_eku_critical.sql
ALTER TABLE "certificate"
    ADD COLUMN "qc_compliance"     BOOLEAN,
    ADD COLUMN "qc_sscd"           BOOLEAN,
    ADD COLUMN "qc_type"           TEXT,      -- serialized JSON array of QcType enum names
    ADD COLUMN "qc_cc_legislation" TEXT;      -- serialized JSON array of ISO 3166-1 alpha-2 codes
