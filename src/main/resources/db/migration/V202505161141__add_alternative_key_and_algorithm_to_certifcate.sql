ALTER TABLE certificate ADD COLUMN alternative_key_uuid UUID REFERENCES cryptographic_key(uuid);
ALTER TABLE certificate ADD COLUMN alternative_signature_algorithm TEXT;
ALTER TABLE certificate ADD COLUMN hybrid_certificate BOOLEAN;
