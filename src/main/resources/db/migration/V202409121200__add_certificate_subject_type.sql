ALTER TABLE certificate ADD COLUMN subject_type TEXT;

UPDATE certificate SET subject_type = 'END_ENTITY' WHERE basic_constraints = 'Subject Type=End Entity' AND (subject_dn_normalized != issuer_dn_normalized);
UPDATE certificate SET subject_type = 'SELF_SIGNED_END_ENTITY' WHERE basic_constraints = 'Subject Type=End Entity' AND (subject_dn_normalized = issuer_dn_normalized);
UPDATE certificate SET subject_type = 'INTERMEDIATE_CA' WHERE basic_constraints = 'Subject Type=CA' AND (subject_dn_normalized != issuer_dn_normalized);
UPDATE certificate SET subject_type = 'ROOT_CA' WHERE basic_constraints = 'Subject Type=CA' AND (subject_dn_normalized = issuer_dn_normalized);

ALTER TABLE certificate DROP COLUMN basic_constraints;
