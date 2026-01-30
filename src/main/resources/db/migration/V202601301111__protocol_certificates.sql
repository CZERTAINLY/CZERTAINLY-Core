UPDATE scep_profile SET ca_certificate_uuid = NULL WHERE (ca_certificate_uuid IS NOT NULL AND ca_certificate_uuid NOT IN (SELECT uuid FROM certificate));
UPDATE cmp_profile SET signing_certificate_uuid = NULL WHERE (signing_certificate_uuid IS NOT NULL AND signing_certificate_uuid NOT IN (SELECT uuid FROM certificate));

ALTER TABLE cmp_profile ADD FOREIGN KEY (signing_certificate_uuid) REFERENCES certificate(uuid) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE scep_profile ADD FOREIGN KEY (ca_certificate_uuid) REFERENCES certificate(uuid) ON DELETE SET NULL ON UPDATE CASCADE;
