CREATE TABLE token_instance_reference (
	uuid UUID NOT NULL,
	i_author VARCHAR NOT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	name VARCHAR NOT NULL,
	token_instance_uuid VARCHAR NOT NULL,
	description VARCHAR NULL DEFAULT NULL,
	status VARCHAR NOT NULL,
	kind VARCHAR NOT NULL,
	connector_uuid UUID NULL DEFAULT NULL,
	connector_name VARCHAR NULL DEFAULT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE token_profile (
	uuid UUID NOT NULL,
	i_author VARCHAR NOT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	name VARCHAR NOT NULL,
	description VARCHAR NULL DEFAULT NULL,
	token_instance_name VARCHAR NULL DEFAULT NULL,
	token_instance_ref_uuid UUID NOT NULL,
	attributes TEXT NULL DEFAULT NULL,
	enabled BOOLEAN NULL DEFAULT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE cryptographic_key (
	uuid UUID NOT NULL,
	i_author VARCHAR NOT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	name VARCHAR NOT NULL,
	token_instance_ref_uuid UUID NOT NULL,
	description VARCHAR NULL DEFAULT NULL,
	cryptographic_algorithm VARCHAR NOT NULL,
	PRIMARY KEY (uuid)
);

alter table if exists token_instance_reference
    add constraint token_instance_reference_to_connector_key
    foreign key (connector_uuid)
    references connector;

alter table if exists token_profile
    add constraint token_profile_to_token_instance_key
    foreign key (token_instance_ref_uuid)
    references token_instance_reference;

alter table if exists cryptographic_key
    add constraint cryptographic_key_to_token_instance_key
    foreign key (token_instance_ref_uuid)
    references token_instance_reference;