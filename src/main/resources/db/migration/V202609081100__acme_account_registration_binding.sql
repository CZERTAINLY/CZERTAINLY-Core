-- An ACME account created with External Account Binding against a certificate pre-registration is bound to that
-- registration for life: one registration, one account. Deleting the certificate leaves the account unbound.
ALTER TABLE acme_account
    ADD COLUMN registration_certificate_uuid UUID NULL;

ALTER TABLE acme_account
    ADD CONSTRAINT uq_acme_account_registration_certificate UNIQUE (registration_certificate_uuid);

ALTER TABLE acme_account
    ADD CONSTRAINT fk_acme_account_registration_certificate FOREIGN KEY (registration_certificate_uuid)
        REFERENCES certificate (uuid) ON DELETE SET NULL;
