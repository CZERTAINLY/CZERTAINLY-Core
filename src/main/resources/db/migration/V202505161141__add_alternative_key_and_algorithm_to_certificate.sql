ALTER TABLE certificate ADD COLUMN alt_key_uuid UUID REFERENCES cryptographic_key(uuid);
ALTER TABLE certificate ADD COLUMN alt_signature_algorithm TEXT;
ALTER TABLE certificate ADD COLUMN alt_public_key_algorithm TEXT;
ALTER TABLE certificate ADD COLUMN alt_key_size INTEGER;

ALTER TABLE certificate ADD COLUMN hybrid_certificate BOOLEAN DEFAULT false;

ALTER TABLE certificate_request ADD COLUMN alt_key_uuid UUID REFERENCES cryptographic_key(uuid);
ALTER TABLE certificate_request ADD COLUMN alt_signature_algorithm TEXT;
