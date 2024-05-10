CREATE TABLE cmp_transaction
(
   uuid              uuid      NOT NULL,
   transaction_id    varchar   NOT NULL,
   certificate_uuid  uuid      NOT NULL,
   cmp_profile_uuid  uuid      NOT NULL
);

ALTER TABLE cmp_transaction
  ADD CONSTRAINT cmp_transaction_to_certificate_key FOREIGN KEY (certificate_uuid)
  REFERENCES certificate (uuid) 
  ON UPDATE NO ACTION
  ON DELETE CASCADE;

ALTER TABLE cmp_transaction
  ADD CONSTRAINT cmp_transaction_to_cmp_profile_key FOREIGN KEY (cmp_profile_uuid)
  REFERENCES cmp_profile (uuid) 
  ON UPDATE NO ACTION
  ON DELETE CASCADE;