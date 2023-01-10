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
	attributes TEXT NULL DEFAULT NULL,
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
	usage VARCHAR NULL DEFAULT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE cryptographic_key (
	uuid UUID NOT NULL,
	i_author VARCHAR NOT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	name VARCHAR NOT NULL,
	token_profile_uuid UUID NULL,
	token_instance_uuid UUID NOT NULL,
	description VARCHAR NULL DEFAULT NULL,
	attributes TEXT NULL DEFAULT NULL,
	owner VARCHAR NULL,
	group_uuid UUID NULL DEFAULT NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE cryptographic_key_item (
	uuid UUID NOT NULL,
	name VARCHAR NOT NULL,
	type VARCHAR NOT NULL,
	key_reference_uuid UUID NOT NULL,
	cryptographic_key_uuid UUID NOT NULL,
	cryptographic_algorithm VARCHAR NULL DEFAULT NULL,
	format VARCHAR NULL,
	key_data VARCHAR NULL,
	state VARCHAR NOT NULL,
	usage VARCHAR NULL DEFAULT NULL,
	enabled BOOLEAN NOT NULL,
	length INTEGER NULL,
	PRIMARY KEY (uuid)
);

CREATE TABLE key_event_history (
	"uuid" UUID NOT NULL,
	"i_cre" TIMESTAMP NOT NULL,
	"i_upd" TIMESTAMP NOT NULL,
	"i_author" VARCHAR NOT NULL,
	"event" VARCHAR NOT NULL,
	"status" VARCHAR NOT NULL,
	"message" VARCHAR NOT NULL,
	"additional_information" VARCHAR NULL DEFAULT NULL,
	"key_uuid" UUID NOT NULL,
	PRIMARY KEY ("uuid")
)
;

alter table if exists key_event_history
    add constraint key_history_to_cryptographic_key_item
    foreign key (key_uuid)
    references cryptographic_key_item
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists token_instance_reference
    add constraint token_instance_reference_to_connector_key
    foreign key (connector_uuid)
    references connector;

alter table if exists token_profile
    add constraint token_profile_to_token_instance_key
    foreign key (token_instance_ref_uuid)
    references token_instance_reference;

alter table if exists cryptographic_key
    add constraint cryptographic_key_to_token_profile_key
    foreign key (token_profile_uuid)
    references token_profile;

alter table if exists cryptographic_key
    add constraint cryptographic_key_to_token_instance_key
    foreign key (token_instance_uuid)
    references token_instance_reference;

alter table if exists cryptographic_key
    add constraint cryptographic_key_to_group_key
    foreign key (group_uuid)
    references certificate_group;

alter table if exists cryptographic_key_item
    add constraint cryptographic_key_to_key_content_key
    foreign key (cryptographic_key_uuid)
    references cryptographic_key;

alter table certificate_group rename to certificate_key_group;

--TODO Add End points and function groups into the database for the Cryptographic Provider