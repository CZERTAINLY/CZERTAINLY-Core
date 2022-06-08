create sequence entity_instance_reference_id_seq start 1 increment 1;
create sequence location_id_seq start 1 increment 1;

create table entity_instance_reference (
    i_author                  varchar(255),
    i_cre                     timestamp not null,
    i_upd                     timestamp not null,
    id                        int8         not null,
    uuid                      varchar(255) not null,
    kind                      varchar(255),
    entity_instance_uuid      varchar(255),
    connector_name            varchar(255),
    name                      varchar(255),
    status                    varchar(255),
    connector_id              int8,
    primary key (id)
);

create table location (
    i_author                  varchar(255),
    i_cre                     timestamp not null,
    i_upd                     timestamp not null,
    id                        int8         not null,
    uuid                      varchar(255) not null,
    attributes                text,
    entity_instance_name      varchar(255),
    description               varchar(255),
    enabled                   boolean,
    name                      varchar(255),
    entity_instance_ref_id    int8,
    support_multi_entries     boolean,
    support_key_mgmt          boolean,
    metadata                  text null default null,
    primary key (id)
);

create table certificate_location (
    push_attributes           text null default null,
    csr_attributes            text null default null,
    metadata                  text null default null,
    with_key                  boolean,
    location_id               int8 not null,
    certificate_id            int8 not null,
    primary key (location_id, certificate_id)
);

alter table if exists entity_instance_reference
    add constraint entity_instance_to_connector_key
    foreign key (connector_id)
    references connector;

alter table if exists location
    add constraint location_to_entity_instance_reference_key
    foreign key (entity_instance_ref_id)
    references entity_instance_reference;

alter table if exists certificate_location
    add constraint certificate_location_to_location_key
    foreign key (location_id)
    references location;

alter table if exists certificate_location
    add constraint certificate_location_to_certificate_key
    foreign key (certificate_id)
    references certificate;

insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'entityProvider',
    'ENTITY_PROVIDER',
    'c703db4a-641e-4a6c-91fb-c73385331d4c');

-- add required endpoints for the entity management in the connector
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities','GET','listEntityInstances',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'d9920198-c227-483b-94a0-1ed03a1d8127'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}','GET','getEntityInstance',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'92af82af-8ea2-4a80-b329-51c506c95ca1'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities','POST','createEntityInstance',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'73a22d51-9592-4ca5-94f5-2080a95543ac'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}','PUT','updateEntityInstance',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'7e6b5ed7-3ffa-4c51-82c8-9f636c61ddb9'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}','DELETE','removeEntityInstance',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'09ffac4c-610b-4da5-98c3-3666e031cde4'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/location/attributes','GET','listLocationAttributes',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'1fd5e94e-9679-48dd-885c-51a7047efba9'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/location/attributes/validate','POST','validateLocationAttributes',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'60cb531b-858f-4073-ab29-b175fa877731');

-- add required endpoints for the location management in the connector
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations','POST','getLocationDetail',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'1d342f3e-d057-44e4-a97b-011ace5c1341'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations/push','POST','pushCertificateToLocation',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'5759565a-c931-4753-b75c-9140632e13dc'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations/push/attributes','GET','listPushCertificateAttributes',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'9cb4ae2d-2ae9-4834-86ed-0d4c32bbf9f4'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations/push/attributes/validate','POST','validatePushCertificateAttributes',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'a5d36436-12ee-4f31-83fd-14e31d0f9a15'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations/remove','POST','removeCertificateFromLocation',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'8c8768e6-1417-4bbc-8d3f-5169e1a7ee08'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations/csr','POST','generateCsrLocation',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'3aa64487-5618-4d40-8824-3c5c9aacb62d'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations/csr/attributes','GET','listGenerateCsrAttributes',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'447c4f7f-2144-4f7a-b944-5b23ef6a6122'),
    (nextval('endpoint_id_seq'),'/v1/entityProvider/entities/{entityUuid}/locations/csr/attributes/validate','POST','validateGenerateCsrAttributes',true,(select id from function_group where code = 'ENTITY_PROVIDER'),'2cd9b012-104c-43aa-b133-36e6b5aa7ef5');

-- remove the old entity data that we do not need anymore

drop sequence if exists endpoint_id_seq;

alter table if exists certificate
    drop constraint FKdiwwxixt707t6nquu7d8k7gga;

alter table if exists certificate
    drop column entity_id;

drop table if exists certificate_entity;