create sequence admin_id_seq start 1 increment 1;
create sequence audit_log_id_seq start 1 increment 1;
create sequence authority_instance_reference_id_seq start 1 increment 1;
create sequence certificate_content_id_seq start 1 increment 1;
create sequence group_id_seq start 1 increment 1;
create sequence entity_id_seq start 1 increment 1;
create sequence certificate_id_seq start 1 increment 1;
create sequence client_id_seq start 1 increment 1;
create sequence connector_2_function_group_id_seq start 1 increment 1;
create sequence connector_id_seq start 1 increment 1;
create sequence credential_id_seq start 1 increment 1;
create sequence discovery_certificate_id_seq start 1 increment 1;
create sequence discovery_id_seq start 1 increment 1;
create sequence endpoint_id_seq start 1 increment 1;
create sequence function_group_id_seq start 1 increment 1;
create sequence ra_profile_id_seq start 1 increment 1;

create table admin (
    id int8 not null,
    uuid varchar(255),
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    description varchar(255),
    email varchar(255),
    enabled boolean,
    name varchar(255),
    role varchar(255),
    serial_number varchar(255),
    surname varchar(255),
    username varchar(255),
    certificate_id int8 not null,
    primary key (id)
);

create table audit_log (
    id int8 not null,
    uuid varchar(255),
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    additional_data text,
    affected varchar(255),
    object_identifier varchar(255),
    operation varchar(255),
    operation_status varchar(255),
    origination varchar(255),
    primary key (id)
);

create table authority_instance_reference (
    id int8 not null,
    uuid varchar(255),
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    kind varchar(255),
    authority_instance_uuid varchar(255),
    connector_name varchar(255),
    name varchar(255),
    status varchar(255),
    connector_id int8,
    primary key (id)
);

CREATE TABLE "certificate" (
    "common_name" VARCHAR,
    "serial_number" VARCHAR NOT NULL,
    "i_author" VARCHAR NULL DEFAULT NULL,
    "i_cre" TIMESTAMP NOT NULL,
    "i_upd" TIMESTAMP NOT NULL,
    "issuer_common_name" VARCHAR NULL DEFAULT '',
    "issuer_dn" VARCHAR NOT NULL DEFAULT '',
    "subject_dn" VARCHAR NOT NULL DEFAULT '',
    "not_before" TIMESTAMP NOT NULL,
    "not_after" TIMESTAMP NOT NULL,
    "public_key_algorithm" VARCHAR NOT NULL DEFAULT '',
    "signature_algorithm" VARCHAR NOT NULL DEFAULT '',
    "key_size" INTEGER NULL DEFAULT NULL,
    "basic_constraints" VARCHAR NULL DEFAULT NULL,
    "extended_key_usage" TEXT NULL DEFAULT NULL,
    "id" BIGINT NOT NULL,
    "uuid" VARCHAR NOT NULL,
    "discovery_uuid" BIGINT NULL DEFAULT NULL,
    "status" VARCHAR NULL DEFAULT 'Unassigned',
    "ra_profile_id" BIGINT NULL DEFAULT NULL,
    "fingerprint" VARCHAR NULL DEFAULT NULL,
    "subject_alternative_names" TEXT NULL DEFAULT NULL,
    "meta" TEXT NULL DEFAULT NULL,
    "entity_id" BIGINT NULL DEFAULT NULL,
    "group_id" BIGINT NULL DEFAULT NULL,
    "owner" VARCHAR NULL DEFAULT '',
    "key_usage" TEXT NULL DEFAULT '',
    "certificate_type" VARCHAR NULL DEFAULT '',
    "issuer_serial_number" VARCHAR NULL DEFAULT NULL,
    "certificate_validation_result" TEXT NULL DEFAULT '',
    "certificate_content_id" BIGINT NULL DEFAULT NULL,
    PRIMARY KEY ("id")
)
;

CREATE TABLE "certificate_content" (
    "id" BIGINT NOT NULL,
    "fingerprint" VARCHAR NOT NULL,
    "content" VARCHAR NOT NULL,
    PRIMARY KEY ("id")
)
;


CREATE TABLE "certificate_entity" (
    "id" BIGINT NOT NULL,
    "name" VARCHAR NOT NULL,
    "uuid" VARCHAR NOT NULL,
    "entity_type" VARCHAR NOT NULL,
    "i_author" VARCHAR NULL DEFAULT NULL,
    "i_cre" DATE NULL DEFAULT NULL,
    "i_upd" DATE NULL DEFAULT NULL,
    "description" VARCHAR NULL DEFAULT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "certificate_group" (
    "id" BIGINT NOT NULL,
    "uuid" VARCHAR NOT NULL,
    "name" VARCHAR NOT NULL,
    "description" VARCHAR NULL DEFAULT NULL,
    "i_author" VARCHAR NULL DEFAULT NULL,
    "i_cre" DATE NULL DEFAULT NULL,
    "i_upd" DATE NULL DEFAULT NULL,
    primary key (id)
);

create table client (
    id int8 not null,
    uuid varchar(255),
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    description varchar(255),
    enabled boolean,
    name varchar(255),
    serial_number varchar(255),
    certificate_id int8 not null,
    primary key (id)
);

create table client_authorization (
    ra_profile_id int8 not null,
    client_id int8 not null,
    primary key (ra_profile_id, client_id)
);

create table connector (
    id int8 not null,
    uuid varchar(255),
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    auth_attributes text,
    auth_type varchar(255),
    name varchar(255),
    status varchar(255),
    url varchar(255),
    primary key (id)
);

create table connector_2_function_group (
    id int8 not null,
    kinds varchar(255),
    connector_id int8 not null,
    function_group_id int8 not null,
    primary key (id)
);

create table credential (
    id int8 not null,
    uuid varchar(255),
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    attributes text,
    connector_name varchar(255),
    enabled boolean,
    name varchar(255),
    kind varchar(255),
    connector_id int8,
    primary key (id)
);

CREATE TABLE "discovery_history" (
    "status" VARCHAR NOT NULL,
    "start_time" TIMESTAMP NULL DEFAULT NULL,
    "end_time" TIMESTAMP NULL DEFAULT NULL,
    "total_certificates_discovered" INTEGER NULL DEFAULT NULL,
    "i_author" VARCHAR NULL DEFAULT NULL,
    "i_cre" TIMESTAMP NULL DEFAULT NULL,
    "i_upd" TIMESTAMP NULL DEFAULT NULL,
    "id" BIGINT NOT NULL,
    "uuid" VARCHAR NOT NULL,
    "connector_uuid" VARCHAR NOT NULL,
    "name" VARCHAR NOT NULL,
    "attributes" TEXT NULL DEFAULT NULL,
    "meta" TEXT NULL DEFAULT NULL,
    "message" TEXT NULL DEFAULT NULL,
    "kind" VARCHAR NULL DEFAULT NULL,
    "connector_name" VARCHAR NULL DEFAULT NULL,
    PRIMARY KEY ("id")
)
;

CREATE TABLE "discovery_certificate" (
    "id" BIGINT NOT NULL,
    "uuid" VARCHAR NOT NULL,
    "common_name" VARCHAR NULL,
    "serial_number" VARCHAR NOT NULL,
    "issuer_common_name" VARCHAR NULL DEFAULT NULL,
    "not_before" VARCHAR NOT NULL,
    "not_after" VARCHAR NOT NULL,
    "i_author" VARCHAR NULL DEFAULT NULL,
    "i_cre" VARCHAR NULL DEFAULT NULL,
    "i_upd" VARCHAR NULL DEFAULT NULL,
    "certificate_content_id" BIGINT NOT NULL,
    "discovery_id" BIGINT NULL DEFAULT NULL,
    PRIMARY KEY ("id")
)
;

create table endpoint (
    id int8 not null,
    uuid varchar(255),
    context varchar(255),
    method varchar(255),
    name varchar(255),
    required boolean,
    function_group_id int8 not null,
    primary key (id)
);

create table function_group (
    id int8 not null,
    uuid varchar(255),
    code varchar(255),
    name varchar(255),
    primary key (id)
);

create table ra_profile (
    id int8 not null,
    uuid varchar(255),
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    attributes text,
    authority_instance_name varchar(255),
    description varchar(255),
    enabled boolean,
    name varchar(255),
    authority_instance_ref_id int8,
    primary key (id)
);

alter table if exists admin
    add constraint FKrq5yjsxacu7105ihrfcp662xe
    foreign key (certificate_id)
    references certificate;

alter table if exists authority_instance_reference
    add constraint FK2t7xntc30lq9crkgdfntk6hsh
    foreign key (connector_id)
    references connector;

alter table if exists certificate
    add constraint FK2ybpa8h8jjfv2cjw76hvv6etw
    foreign key (certificate_content_id)
    references certificate_content;

alter table if exists certificate
    add constraint FKdiwwxixt707t6nquu7d8k7gga
    foreign key (entity_id)
    references certificate_entity;

alter table if exists certificate
    add constraint FKcuayu1tjhuojrg3c28i2uqi4g
    foreign key (group_id)
    references certificate_group;

alter table if exists certificate
    add constraint FK41nap2d0f529tuabyjs424a80
    foreign key (ra_profile_id)
    references ra_profile;

alter table if exists client
    add constraint FKlyok3m28dnf8lr2gevt5aj2np
    foreign key (certificate_id)
    references certificate;

alter table if exists client_authorization
    add constraint FKkjs42uvpsdos793wflp2onl3s
    foreign key (client_id)
    references client;

alter table if exists client_authorization
    add constraint FKdn5d25h79l2el4iv9w7isnnjc
    foreign key (ra_profile_id)
    references ra_profile;

alter table if exists connector_2_function_group
    add constraint FK1qvna5aqsvmfwsxr90q9ewsk3
    foreign key (connector_id)
    references connector;

alter table if exists connector_2_function_group
    add constraint FKe04tlwcpn0a6otrw84gke8k3d
    foreign key (function_group_id)
    references function_group;

alter table if exists credential
    add constraint FKrxdkw4wef9tt0fbx5t892wv59
    foreign key (connector_id)
    references connector;

alter table if exists discovery_certificate
    add constraint FKgmcpy0hkmray7pk0hvqpc4nwc
    foreign key (certificate_content_id)
    references certificate_content;

alter table if exists discovery_certificate
    add constraint FK4uptmj2ejf9i1cfjnikmesa5p
    foreign key (discovery_id)
    references discovery_history;

alter table if exists endpoint
    add constraint FKgj4l79prijfj4nnjl7idi27be
    foreign key (function_group_id)
    references function_group;

alter table if exists ra_profile
    add constraint FK1ybgp06wf8uoegwfhsg4fev2a
    foreign key (authority_instance_ref_id)
    references authority_instance_reference;

ALTER TABLE if exists client
    ADD CONSTRAINT client_name_unique
    UNIQUE (name);

ALTER TABLE if exists admin
    ADD CONSTRAINT admin_username_unique
    UNIQUE (username);

ALTER TABLE if exists certificate
    ADD CONSTRAINT certificate_uuid_unique
    UNIQUE (uuid);

ALTER TABLE if exists discovery_history
    ADD CONSTRAINT discovery_uuid_unique
    UNIQUE (uuid);

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'credentialProvider',
    'CREDENTIAL_PROVIDER',
    'e8ae0a8c-ed12-4f63-804f-2659ee9dff6e');

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'authorityProvider',
    'AUTHORITY_PROVIDER',
    '736b0fd6-5ea0-4e10-abe7-cfed39cc2a1a');

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'legacyAuthorityProvider',
    'LEGACY_AUTHORITY_PROVIDER',
    '435ee47f-fd03-4c50-ae6f-ca60f4829023');

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'discoveryProvider',
    'DISCOVERY_PROVIDER',
    'a6c4042f-9465-4476-9528-1efd6caaf944');

insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','GET','listAuthorityInstances',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'e3521dd0-e150-4676-a79c-30a33e62889c'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','GET','getAuthorityInstance',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'924ac89a-7376-4ac8-8c15-ecb7d9e8ca16'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','POST','createAuthorityInstance',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'9bf9cd3b-73de-4c1c-a712-7396e9dc78e5'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','POST','updateAuthorityInstance',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'51c5b673-0e6e-4b8d-a31b-1b35835b4025'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','DELETE','removeAuthorityInstance',false,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'f83d858a-d63b-48e7-b22c-fdb7f7e3d9b1'),
    (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'ecdf6214-a491-4a0f-9084-7b502a16315e'),
    (nextval('endpoint_id_seq'),'/v1/discoveryProvider/discover','POST','discoverCertificate',true,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'784f8681-e3ea-4d8d-938a-ce315752cd80'),
    (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'eb8645ee-5def-4b77-8c66-f8c85da88132'),
    (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'CREDENTIAL_PROVIDER'),'886eee93-8a82-4fa0-bee0-60eb4bed766f'),
    (nextval('endpoint_id_seq'),'/v1/credentialProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select id from function_group where code = 'CREDENTIAL_PROVIDER'),'45c825bb-5e3a-42f4-8808-9107d4966078');
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/credentialProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select id from function_group where code = 'CREDENTIAL_PROVIDER'),'eb159884-f7ee-457e-918c-5f7f6e2f2597'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'072349d4-d1a0-4398-b4e5-88fba454d815'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'59070334-e550-466c-b538-bd8d2d9b06e5'),
    (nextval('endpoint_id_seq'),'/v1/discoveryProvider/{kind}/attributes/validate','POST','validateAttributes',true,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'61f1096e-5798-40cd-874b-de7b782f1d17'),
    (nextval('endpoint_id_seq'),'/v1/discoveryProvider/{kind}/attributes','GET','listAttributeDefinitions',true,(select id from function_group where code = 'DISCOVERY_PROVIDER'),'8995b6ef-ea7c-417b-a298-f0a4a8a4f55c'),
    (nextval('endpoint_id_seq'),'/v1','GET','listSupportedFunctions',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'cf4af237-164e-4326-8a34-80c90d53b2d7'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities','GET','listEndEntities',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'5a78b374-3113-4310-a35d-45a8a2a04eca'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes/validate','POST','validateAttributes',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'ca07a81d-724f-4304-8ffa-3cb405766301'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/{kind}/attributes','GET','listAttributeDefinitions',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'ca0595ad-36e5-4060-a19d-e80b8f7461fd'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}','DELETE','removeEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'e881624f-af84-41fd-aeb8-a90e342bb131');
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}/resetPassword','PUT','resetPassword',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'f2a6f043-3fb2-4f9d-9996-ce8cf68d2ad9'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles','GET','listEntityProfiles',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'e13b274b-bdbd-4b4d-a5fa-875f0a6594e9'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileId}/certificateprofiles','GET','listCertificateProfiles',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'b2a2a828-598b-47dd-a1c5-ce877989153f'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileId}/cas','GET','listCAsInProfile',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'4bef1a55-4725-48af-911e-9a051784c4c4'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/certificates/revoke','POST','revokeCertificate',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'656e4414-d735-457f-ad43-f921c5af4507'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/certificates/issue','POST','issueCertificate',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'a91dd6df-cd2c-46f4-af09-3693a167118d'),
    (nextval('endpoint_id_seq'),'/v1/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}','GET','getEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'a8b1d647-6a8e-46fd-b4e1-844b30df4dcc'),
    (nextval('endpoint_id_seq'),'/v1/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities','POST','createEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'5f61c054-0d68-44b1-b326-2ed28a2a55fa'),
    (nextval('endpoint_id_seq'),'/v1/authorities/{uuid}/endEntityProfiles/{endEntityProfileName}/endEntities/{endEntityName}','POST','updateEndEntity',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'57320a6d-3763-4a25-bdae-4a2a92a67487'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','DELETE','removeAuthorityInstance',false,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'b3592167-af2a-44b3-89d2-e4bfd000caa4');
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','POST','createAuthorityInstance',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'6350c3bb-57ef-4416-964b-0254df28131e'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','GET','getAuthorityInstance',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'1692cec0-50aa-46a3-be7f-b32e6a752d2a'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities','GET','listAuthorityInstances',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'cb1ae7eb-a97b-44bd-bf76-46ae96e32985'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}','POST','updateAuthorityInstance',true,(select id from function_group where code = 'LEGACY_AUTHORITY_PROVIDER'),'06f1f14f-328b-40f7-8f34-f168619e3a3a'),
    (nextval('endpoint_id_seq'),'/v1/authorityProvider/authorities/{uuid}/raProfile/attributes','GET','listRAProfileAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'e43155b6-51ad-46e0-a60c-176ee5e6dfea'),
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/issue','POST','issueCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'065dbfba-63f9-4011-abe4-f2ca6d224521'),
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/issue/attributes','GET','listIssueCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'0288132c-5d9c-4db8-97a1-7ef977b45b17');
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/issue/attributes/validate','POST','validateIssueCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'355d306e-75f7-4b85-848b-58bddf95c582'),
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/renew','POST','renewCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'efdb9bcd-4f7c-473b-8704-77b12b3f6d33'),
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/revoke/attributes','GET','listRevokeCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'2dcc528b-9e16-46c6-877e-74eae258173f'),
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/revoke/attributes/validate','POST','validateRevokeCertificateAttributes',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'f28a2c14-1183-430d-a908-85bcfda56dab'),
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/revoke','POST','revokeCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'d9e162ae-2d50-4e98-bc37-62d015c43199'),
    (nextval('endpoint_id_seq'),'/v2/authorityProvider/authorities/{uuid}/certificates/revoke','POST','revokeCertificate',true,(select id from function_group where code = 'AUTHORITY_PROVIDER'),'7085fad6-df6e-4697-9c8e-7c80c2a12bd7');
