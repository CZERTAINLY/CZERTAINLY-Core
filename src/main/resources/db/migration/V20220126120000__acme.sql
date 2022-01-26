create sequence acme_new_account_id_seq start 1 increment 1;
create sequence acme_new_authorization_id_seq start 1 increment 1;
create sequence acme_new_challenge_id_seq start 1 increment 1;
create sequence acme_new_order_id_seq start 1 increment 1;
create sequence acme_profile_id_seq start 1 increment 1;

CREATE TABLE acme_account (
	id BIGINT NOT NULL,
	uuid VARCHAR(36) NOT NULL,
	i_cre TIMESTAMP NULL DEFAULT NULL,
	i_author VARCHAR(255) NULL DEFAULT NULL,
	i_upd TIMESTAMP NULL DEFAULT NULL,
	account_id VARCHAR(255) NOT NULL,
	public_key VARCHAR NOT NULL,
	is_default_ra_profile BOOLEAN NULL DEFAULT NULL,
	status VARCHAR NOT NULL,
	contact TEXT NULL DEFAULT NULL,
	terms_of_service_agreed BOOLEAN NOT NULL,
	acme_profile_id BIGINT NULL DEFAULT NULL,
	ra_profile_id BIGINT NULL DEFAULT NULL,
	is_enabled BOOLEAN NOT NULL,
	PRIMARY KEY (id),
);

CREATE TABLE acme_authorization (
	id BIGINT NOT NULL,
	uuid VARCHAR(255) NOT NULL,
	i_author VARCHAR(255) NULL DEFAULT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	authorization_id VARCHAR NOT NULL,
	identifier TEXT NULL DEFAULT NULL,
	status VARCHAR NOT NULL,
	expires TIMESTAMP NULL DEFAULT NULL,
	wildcard BOOLEAN NULL DEFAULT NULL,
	order_id BIGINT NOT NULL,
	PRIMARY KEY (id)
)
;

CREATE TABLE acme_challenge (
	id BIGINT NOT NULL,
	uuid VARCHAR NOT NULL,
	i_author VARCHAR NULL DEFAULT NULL,
	i_cre TIMESTAMP NULL DEFAULT NULL,
	i_upd TIMESTAMP NULL DEFAULT NULL,
	challenge_id VARCHAR NOT NULL,
	type VARCHAR NOT NULL,
	token VARCHAR NULL DEFAULT NULL,
	status VARCHAR NOT NULL,
	authorization_id BIGINT NULL DEFAULT NULL,
	validated TIMESTAMP NULL DEFAULT NULL,
	PRIMARY KEY (id)
)
;

CREATE TABLE acme_nonce (
	nonce VARCHAR(250) NOT NULL,
	created TIMESTAMP NOT NULL,
	expires TIMESTAMP NOT NULL
)
;

CREATE TABLE acme_order (
	id BIGINT NOT NULL,
	uuid VARCHAR(255) NOT NULL,
	i_author VARCHAR(255) NULL DEFAULT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	order_id VARCHAR NOT NULL,
	account_id BIGINT NOT NULL,
	not_before TIMESTAMP NULL DEFAULT NULL,
	not_after TIMESTAMP NULL DEFAULT NULL,
	expires TIMESTAMP NULL DEFAULT NULL,
	identifiers TEXT NULL DEFAULT NULL,
	status VARCHAR NOT NULL,
	certificate_ref BIGINT NULL DEFAULT NULL,
	certificate_id VARCHAR NULL DEFAULT NULL,
	PRIMARY KEY (id)
)
;

CREATE TABLE acme_profile (
    id BIGINT NOT NULL,
    uuid VARCHAR NOT NULL,
    i_author VARCHAR NULL DEFAULT NULL,
    i_cre TIMESTAMP NULL DEFAULT NULL,
    i_upd TIMESTAMP NULL DEFAULT NULL,
    name VARCHAR NOT NULL,
    is_enabled BOOLEAN NOT NULL,
    terms_of_service_url VARCHAR NULL DEFAULT NULL,
    dns_resolver_ip VARCHAR NULL DEFAULT NULL,
    dns_resolver_port VARCHAR NULL DEFAULT NULL,
    ra_profile_id BIGINT NULL DEFAULT NULL,
    issue_certificate_attributes TEXT NULL DEFAULT NULL,
    revoke_certificate_attributes TEXT NULL DEFAULT NULL,
    website_url VARCHAR NULL DEFAULT NULL,
    validity BIGINT NULL DEFAULT NULL,
    retry_interval BIGINT NULL DEFAULT NULL,
    terms_of_service_change_approval BOOLEAN NULL DEFAULT NULL,
    description VARCHAR NULL DEFAULT NULL,
    insist_contact BOOLEAN NULL DEFAULT NULL,
    insist_terms_of_service BOOLEAN NULL DEFAULT NULL,
    terms_of_service_change_url VARCHAR NULL DEFAULT NULL,
    PRIMARY KEY (id)
)
;

alter table ra_profile add column acme_profile_id bigint;
alter table ra_profile add column acme_issue_certificate_attributes text;
alter table ra_profile add column acme_revoke_certificate_attributes text;

alter table if exists acme_account
    add constraint acme_account_to_ra_profile_key
    foreign key (ra_profile_id)
    references ra_profile
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_account
    add constraint acme_profile_to_acme_account_key
    foreign key (acme_profile_id)
    references acme_profile
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_authorization
    add constraint acme_authorization_to_order_key
    foreign key (order_id)
    references acme_order
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_challenge
    add constraint acme_challenge_to_authorization_key
    foreign key (authorization_id)
    references acme_authorization
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_order
    add constraint acme_order_to_account_key
    foreign key (account_id)
    references acme_account
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_order
    add constraint acme_order_to_certificate_key
    foreign key (certificate_ref)
    references certificate
    ON UPDATE NO ACTION ON DELETE CASCADE;


alter table if exists acme_profile
    add constraint acme_profile_to_ra_profile_key
    foreign key (ra_profile_id)
    references ra_profile;

alter table if exists ra_profile
    add constraint ra_profile_to_acme_profile_key
    foreign key (acme_profile_id)
    references acme_profile;