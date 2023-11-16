ALTER TABLE certificate ADD COLUMN trusted_ca BOOLEAN NULL;
UPDATE certificate SET trusted_ca = false WHERE basic_constraints LIKE '%Subject Type=CA%';
