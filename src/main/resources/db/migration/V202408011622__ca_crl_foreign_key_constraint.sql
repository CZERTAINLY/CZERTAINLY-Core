ALTER TABLE crl DROP CONSTRAINT crl_ca_certificate_uuid_fkey;

ALTER TABLE crl
ADD CONSTRAINT crl_ca_certificate_uuid_fkey
FOREIGN KEY (ca_certificate_uuid)
REFERENCES certificate(uuid)
ON UPDATE CASCADE
ON DELETE SET NULL;


