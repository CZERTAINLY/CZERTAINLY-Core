-- Rename the CONTENT_SIGNING signing workflow type to DOCUMENT_SIGNING (DP-13).
-- Both workflow_type columns and connector_interface.features are mapped with
-- @Enumerated(EnumType.STRING), so they persist the Java constant name rather
-- than the wire code. Hibernate resolves them via Enum.valueOf and throws on an
-- unknown value, so every stored occurrence must be rewritten here.
UPDATE "signing_profile_version"
   SET "workflow_type" = 'DOCUMENT_SIGNING'
 WHERE "workflow_type" = 'CONTENT_SIGNING';

-- Denormalized cache of the latest signing_profile_version row.
UPDATE "signing_profile"
   SET "workflow_type" = 'DOCUMENT_SIGNING'
 WHERE "workflow_type" = 'CONTENT_SIGNING';

-- FeatureFlag.CONTENT_SIGNING was renamed in the same contract change; the flag
-- is stored as an element of the connector_interface.features text array.
UPDATE "connector_interface"
   SET "features" = array_replace("features", 'CONTENT_SIGNING', 'DOCUMENT_SIGNING')
 WHERE 'CONTENT_SIGNING' = ANY ("features");
