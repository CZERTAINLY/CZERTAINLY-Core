CREATE TABLE cmp_profile
(
    uuid                          UUID         NOT NULL,
    i_author                      VARCHAR(255) NULL     DEFAULT NULL,
    i_cre                         TIMESTAMP    NOT NULL,
    i_upd                         TIMESTAMP    NOT NULL,
    description                   VARCHAR(255) NULL     DEFAULT NULL,
    enabled                       BOOLEAN      NOT NULL,
    name                          VARCHAR(255) NOT NULL DEFAULT NULL,
    issue_certificate_attributes  TEXT         NULL     DEFAULT NULL,
    revoke_certificate_attributes TEXT         NULL     DEFAULT NULL,
    ra_profile_uuid               UUID         NULL     DEFAULT NULL,

    request_protection_method     VARCHAR(255) NULL     DEFAULT NULL, -- supported PKI message protection methods (5.1.3)
    response_protection_method    VARCHAR(255) NULL     DEFAULT NULL, -- supported PKI message protection methods (5.1.3)
    shared_secret                 VARCHAR(255) NULL     DEFAULT NULL, -- shared secret for "sharedSecret" protection method
    signing_certificate_uuid      UUID,                               -- signing certificate for "signature" protection method
    variant                       VARCHAR(255) NOT NULL DEFAULT 'V2', -- profile variant

    PRIMARY KEY (uuid)
);

CREATE TABLE cmp_transaction
(
    uuid             uuid         NOT NULL,
    transaction_id   varchar      NOT NULL,
    certificate_uuid uuid         NOT NULL,
    cmp_profile_uuid uuid         NOT NULL,
    state            VARCHAR(255) NULL DEFAULT NULL,
    custom_reason    VARCHAR(255) NULL DEFAULT NULL
);

ALTER TABLE ra_profile_protocol_attribute
    ADD COLUMN cmp_issue_certificate_attributes  TEXT NULL DEFAULT NULL,
    ADD COLUMN cmp_revoke_certificate_attributes TEXT NULL DEFAULT NULL;

ALTER TABLE ra_profile
    ADD COLUMN cmp_profile_uuid UUID NULL DEFAULT NULL;

ALTER TABLE ra_profile
    ADD CONSTRAINT ra_profile_to_cmp_profile_uuid FOREIGN KEY (cmp_profile_uuid)
        REFERENCES cmp_profile (uuid)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION;

ALTER TABLE cmp_profile
    ADD CONSTRAINT cmp_profile_to_ra_profile_key FOREIGN KEY (ra_profile_uuid)
        REFERENCES ra_profile (uuid)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION;

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
