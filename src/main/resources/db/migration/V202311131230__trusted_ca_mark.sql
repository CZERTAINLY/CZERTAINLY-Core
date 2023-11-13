ALTER TABLE certificate ADD COLUMN trusted_ca BOOLEAN NULL;
UPDATE certificate
SET trusted_ca = CASE WHEN basic_constraints LIKE '%Subject Type=CA%' AND trusted_ca is NULL THEN false ELSE NULL END;
