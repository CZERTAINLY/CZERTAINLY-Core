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
	"common_name" VARCHAR NOT NULL,
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
	"common_name" VARCHAR NOT NULL,
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

    alter table if exists ca_instance_reference
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
       foreign key (discovery_uuid)
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
