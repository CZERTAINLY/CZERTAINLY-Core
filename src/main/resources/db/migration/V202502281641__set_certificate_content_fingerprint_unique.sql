ALTER TABLE certificate_content ADD UNIQUE (fingerprint);
ALTER TABLE certificate_content ALTER COLUMN id SET DEFAULT nextval('certificate_content_id_seq');

