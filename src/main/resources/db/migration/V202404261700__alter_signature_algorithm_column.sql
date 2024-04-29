ALTER TABLE certificate alter COLUMN  "signature_algorithm" drop not null;
ALTER TABLE certificate_request alter COLUMN  "signature_algorithm" drop not null;
