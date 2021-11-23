create sequence credential_id_seq start 1 increment 1;
create sequence connector_id_seq start 1 increment 1;
create sequence ca_instance_reference_id_seq start 1 increment 1;

create table ca_instance_reference (
   id int8 not null,
    i_author varchar(255),
    i_cre timestamp not null,
    i_upd timestamp not null,
    ca_instance_id int8,
    name varchar(255),
    status varchar(255),
    connector_id int8 not null,
    primary key (id)
);

create table credential
(
    id                      int8         not null,
    i_author                varchar(255),
    i_cre                   timestamp    not null,
    i_upd                   timestamp    not null,
    name                    varchar(255),
    type                    varchar(255),
    attributes              text,
    credential_provider_id  int8         not null,
    primary key (id)
);

create table connector (
    id          int8            not null,
    i_author    varchar(255),
    i_cre       timestamp       not null,
    i_upd       timestamp       not null,
    kind        varchar(255),
    name        varchar(255),
    status      varchar(255),
    type        varchar(255),
    url         varchar(255),
    primary key (id)
);

alter table audit_log add column uuid varchar(36);
alter table admin add column uuid varchar(36);
alter table client add column uuid varchar(36);

alter table ra_profile add column uuid varchar(36);
alter table ra_profile add column ca_instance_ref_id int8;