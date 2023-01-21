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
	uuid UUID NOT NULL,
	i_cre TIMESTAMP NOT NULL,
	i_upd TIMESTAMP NOT NULL,
	i_author VARCHAR NOT NULL,
	event VARCHAR NOT NULL,
	status VARCHAR NOT NULL,
	message VARCHAR NOT NULL,
	additional_information VARCHAR NULL DEFAULT NULL,
	key_uuid UUID NOT NULL,
	PRIMARY KEY (uuid)
)
;

alter table certificate add column key_uuid UUID;
alter table certificate add column csr TEXT NULL DEFAULT NULL;
alter table certificate add column csr_attributes TEXT NULL DEFAULT NULL;
alter table certificate add column signature_attributes TEXT NULL DEFAULT NULL;


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

alter table if exists certificate
    add constraint certificate_to_cryptographic_key
    foreign key (key_uuid)
    references cryptographic_key;

alter table certificate_group rename to certificate_key_group;

insert into function_group (name, code, uuid) values (
    'cryptographyProvider',
    'CRYPTOGRAPHY_PROVIDER',
    'c2fece10-9896-11ed-a8fc-0242ac120002');

-- add required endpoints for the compliance and rules management in the connector
insert into endpoint (context,method,name,required,function_group_uuid,uuid) values
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/{keyUuid}/encrypt','POST','encryptData',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f78a72-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/random','POST','randomData',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f78db0-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/activate','PATCH','activateTokenInstance',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f78ee6-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/{keyUuid}','GET','getKey',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f79044-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys','GET','listKeys',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f792b0-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/pair/attributes','GET','listCreateKeyPairAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f79724-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/tokenProfile/attributes/validate','POST','validateTokenProfileAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7985a-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/pair','POST','createKeyPair',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f79a8a-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/activate/attributes/validate','POST','validateTokenInstanceActivationAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f79ba2-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens','POST','createTokenInstance',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f79dfa-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/random/attributes','GET','listRandomAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f79efe-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/{keyUuid}/decrypt','POST','decryptData',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7a0f2-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}','POST','updateTokenInstance',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7a1ec-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7a5f2-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/secret/attributes/validate','POST','validateCreateSecretKeyAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7a714-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/pair/attributes/validate','POST','validateCreateKeyPairAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7a80e-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/status','GET','getTokenInstanceStatus',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7a908-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/activate/attributes','GET','listTokenInstanceActivationAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7ac14-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}','GET','getTokenInstance',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7ad18-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/tokenProfile/attributes','GET','listTokenProfileAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7af20-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/callbacks/token/{option}/attributes','GET','getCreateTokenAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b13c-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/secret','POST','createSecretKey',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b240-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens','GET','listTokenInstances',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b344-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/random/attributes/validate','POST','validateRandomAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b43e-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/{keyUuid}/sign','POST','signData',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b6a0-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b7a4-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/{keyUuid}','DELETE','destroyKey',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b89e-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/{keyUuid}/verify','POST','verifyData',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7b998-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/deactivate','PATCH','deactivateTokenInstance',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7baa6-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}/keys/secret/attributes','GET','listCreateSecretKeyAttributes',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7bd9e-9896-11ed-a8fc-0242ac120002'),
    ('/v1/cryptographyProvider/tokens/{uuid}','DELETE','removeTokenInstance',true,(select uuid from function_group where code = 'CRYPTOGRAPHY_PROVIDER'),'83f7beac-9896-11ed-a8fc-0242ac120002');

-- Update discovery table connector UUID from String to UUID
ALTER TABLE discovery_history ALTER COLUMN connector_uuid SET DATA TYPE UUID USING "connector_uuid"::UUID;