CREATE TABLE cmp_profile (
    uuid UUID NOT NULL,
    i_author VARCHAR(255) NULL DEFAULT NULL,
    i_cre TIMESTAMP NOT NULL,
    i_upd TIMESTAMP NOT NULL,
    description VARCHAR(255) NULL DEFAULT NULL,
    enabled BOOLEAN NOT NULL,
    name VARCHAR(255) NOT NULL DEFAULT NULL,
    issue_certificate_attributes TEXT NULL DEFAULT NULL,
    revoke_certificate_attributes TEXT NULL DEFAULT NULL,
    ra_profile_uuid UUID NULL DEFAULT NULL,

    request_protection_method VARCHAR(255) NULL DEFAULT NULL, -- supported PKI message protection methods (5.1.3) - "sharedSecret" or "signature"

    require_manual_approval BOOLEAN NOT NULL,
    ca_certificate_uuid UUID NOT NULL,
    challenge_password VARCHAR NULL DEFAULT NULL,
    include_ca_certificate BOOLEAN NULL DEFAULT NULL,
    include_ca_certificate_chain BOOLEAN NULL DEFAULT NULL,
    renew_threshold INTEGER NULL DEFAULT NULL,
    PRIMARY KEY (uuid)
);

ALTER TABLE ra_profile_protocol_attribute
    ADD COLUMN cmp_issue_certificate_attributes TEXT NULL DEFAULT NULL,
    ADD COLUMN cmp_revoke_certificate_attributes TEXT NULL DEFAULT NULL;

alter table ra_profile add column cmp_profile_uuid UUID NULL DEFAULT NULL;

alter table if exists ra_profile
    add constraint ra_profile_to_cmp_profile_uuid
    foreign key (cmp_profile_uuid)
    references cmp_profile
    ON UPDATE NO ACTION ON DELETE NO ACTION;

alter table if exists cmp_profile
    add constraint cmp_profile_to_ra_profile_key
    foreign key (ra_profile_uuid)
    references ra_profile
    ON UPDATE NO ACTION ON DELETE NO ACTION;
