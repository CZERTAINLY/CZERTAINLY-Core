CREATE TABLE scep_profile (
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
	require_manual_approval BOOLEAN NOT NULL,
	ca_certificate_uuid UUID NOT NULL,
	challenge_password VARCHAR NULL DEFAULT NULL,
	include_ca_certificate BOOLEAN NULL DEFAULT NULL,
	include_ca_certificate_chain BOOLEAN NULL DEFAULT NULL,
	renew_threshold INTEGER NULL DEFAULT NULL,
	PRIMARY KEY (uuid)
	);

CREATE TABLE scep_transaction (
	uuid UUID NOT NULL,
	transaction_id VARCHAR NOT NULL,
	certificate_uuid UUID NOT NULL,
	scep_profile_uuid UUID NOT NULL
	);
	
CREATE TABLE ra_profile_protocol_attribute (
	uuid UUID NOT NULL,
	ra_profile_uuid UUID NOT NULL,
	acme_issue_certificate_attributes TEXT NULL DEFAULT NULL,
	acme_revoke_certificate_attributes TEXT NULL DEFAULT NULL,
	scep_issue_certificate_attributes TEXT NULL DEFAULT NULL,
	PRIMARY KEY (uuid))
;

alter table ra_profile add column scep_profile_uuid UUID NULL DEFAULT NULL;

alter table if exists ra_profile
    add constraint ra_profile_to_scep_profile_uuid
    foreign key (scep_profile_uuid)
    references scep_profile
    ON UPDATE NO ACTION ON DELETE NO ACTION;

alter table if exists scep_profile
    add constraint scep_profile_to_certificate_key
    foreign key (ca_certificate_uuid)
    references certificate
    ON UPDATE NO ACTION ON DELETE NO ACTION;

alter table if exists scep_profile
    add constraint scep_profile_to_ra_profile_key
    foreign key (ra_profile_uuid)
    references ra_profile
    ON UPDATE NO ACTION ON DELETE NO ACTION;

alter table if exists scep_transaction
    add constraint scep_transaction_to_certificate_key
    foreign key (certificate_uuid)
    references certificate
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists scep_transaction
    add constraint scep_transaction_to_scep_profile_key
    foreign key (scep_profile_uuid)
    references scep_profile
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists ra_profile_protocol_attribute
    add constraint ra_profile_protocol_attribute_to_ra_profile
    foreign key (ra_profile_uuid)
    references ra_profile
    ON UPDATE NO ACTION ON DELETE CASCADE;

INSERT INTO ra_profile_protocol_attribute(uuid, ra_profile_uuid, acme_issue_certificate_attributes, acme_revoke_certificate_attributes)
SELECT uuid, uuid, acme_issue_certificate_attributes, acme_revoke_certificate_attributes
FROM ra_profile;

alter table ra_profile drop column acme_issue_certificate_attributes;
alter table ra_profile drop column acme_revoke_certificate_attributes;