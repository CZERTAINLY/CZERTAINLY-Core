ALTER TABLE secret ADD COLUMN compliance_status VARCHAR NOT NULL DEFAULT 'NOT_CHECKED';
ALTER TABLE secret ADD COLUMN compliance_result jsonb;
