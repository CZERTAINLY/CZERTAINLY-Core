create sequence compliance_profile_id_seq start 1 increment 1;
create sequence compliance_rule_id_seq start 1 increment 1;
create sequence compliance_group_id_seq start 1 increment 1;
create sequence compliance_profile_rule_id_seq start 1 increment 1;


-- Add compliance profile related table
CREATE TABLE "compliance_group" (
	"id" BIGINT NOT NULL,
	"uuid" VARCHAR NOT NULL,
	"name" VARCHAR NOT NULL,
	"kind" VARCHAR NOT NULL,
	"description" TEXT NULL DEFAULT NULL,
	"decommissioned" BOOLEAN NULL DEFAULT NULL,
	"connector_id" BIGINT NOT NULL,
	PRIMARY KEY ("id")
)
;

CREATE TABLE "compliance_profile" (
	"id" BIGINT NOT NULL,
	"uuid" VARCHAR NOT NULL,
	"i_author" VARCHAR NOT NULL,
	"i_cre" TIMESTAMP NOT NULL,
	"i_upd" TIMESTAMP NOT NULL,
	"name" VARCHAR NOT NULL,
	"description" VARCHAR NULL DEFAULT NULL,
	PRIMARY KEY ("id")
)
;

CREATE TABLE "compliance_profile_rule" (
	"id" BIGINT NOT NULL,
	"uuid" VARCHAR NOT NULL,
	"i_author" VARCHAR NOT NULL,
	"i_cre" TIMESTAMP NOT NULL,
	"i_upd" TIMESTAMP NOT NULL,
	"rule_id" BIGINT NOT NULL,
	"attributes" TEXT NULL DEFAULT NULL,
	"compliance_profile_id" BIGINT NOT NULL,
	PRIMARY KEY ("id")
)
;

CREATE TABLE "compliance_rule" (
	"id" BIGINT NOT NULL,
	"name" VARCHAR NOT NULL,
	"uuid" VARCHAR NOT NULL,
	"kind" VARCHAR NOT NULL,
	"decommissioned" BOOLEAN NULL DEFAULT NULL,
	"certificate_type" VARCHAR NOT NULL,
	"attributes" TEXT NULL DEFAULT NULL,
	"description" VARCHAR NULL DEFAULT NULL,
	"group_id" BIGINT NULL DEFAULT NULL,
	"connector_id" BIGINT NOT NULL,
	PRIMARY KEY ("id")
)
;

CREATE TABLE "compliance_profile_2_compliance_group" (
	"profile_id" BIGINT NULL DEFAULT NULL,
	"group_id" BIGINT NULL DEFAULT NULL
	);

CREATE TABLE "ra_profile_2_compliance_profile" (
	"ra_profile_id" BIGINT NULL DEFAULT NULL,
	"compliance_profile_id" BIGINT NULL DEFAULT NULL
	);


insert into function_group (id, name, code, uuid) values (
    nextval('function_group_id_seq'),
    'complianceProvider',
    'COMPLIANCE_PROVIDER',
    '7bd77d90-f7d7-11ec-8fea-0242ac120002');

-- add required endpoints for the compliance and rules management in the connector
insert into endpoint (id,context,"method","name",required,function_group_id,uuid) values
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/rules','GET','listRules',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd77d90-e7d7-11ec-8fea-0242ac120002'),
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/groups','GET','listGroups',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd77f34-e7d7-11ec-8fea-0242ac120002'),
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/groups/{uuid}','GET','listGroupDetails',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd78038-e7d7-11ec-8fea-0242ac120002'),
    (nextval('endpoint_id_seq'),'/v1/complianceProvider/{kind}/compliance','POST','checkCompliance',true,(select id from function_group where code = 'COMPLIANCE_PROVIDER'),'7bd7811e-e7d7-11ec-8fea-0242ac120002');



alter table if exists ra_profile_2_compliance_profile
    add constraint COMPLIANCE_PROFILE_TO_MAPPING_KEY
    foreign key (compliance_profile_id)
    references compliance_profile
    ON DELETE CASCADE;

alter table if exists ra_profile_2_compliance_profile
    add constraint RA_PROFILE_TO_COMPLIANCE_MAPPING_KEY
    foreign key (ra_profile_id)
    references ra_profile
    ON DELETE CASCADE;


alter table if exists compliance_profile_2_compliance_group
    add constraint COMPLIANCE_GROUP_TO_MAPPING_KEY
    foreign key (group_id)
    references compliance_group;

alter table if exists compliance_profile_2_compliance_group
    add constraint COMPLIANCE_PROFILE_TO_MAPPING_KEY
    foreign key (profile_id)
    references compliance_profile
    ON DELETE CASCADE;


alter table if exists compliance_profile
    add constraint COMPLIANCE_GROUP_TO_CONNECTOR
    foreign key (compliance_group_id)
    references compliance_group;

alter table if exists compliance_group
    add constraint COMPLIANCE_GROUP_TO_CONNECTOR
    foreign key (connector_id)
    references connector ON DELETE CASCADE;

alter table if exists compliance_profile_rule
    add constraint COMPLIANCE_PROFILE_RULE_TO_COMPLIANCE_PROFILE
    foreign key (compliance_profile_id)
    references compliance_profile;

alter table if exists compliance_profile_rule
    add constraint COMPLIANCE_PROFILE_RULE_TO_COMPLIANCE_RULE
    foreign key (rule_id)
    references compliance_rule;


alter table if exists compliance_rule
    add constraint COMPLIANCE_RULE_TO_COMPLIANCE_GROUP_KEY
    foreign key (group_id)
    references compliance_group;

alter table if exists compliance_rule
    add constraint COMPLIANCE_RULE_TO_CONNECTOR_KEY
    foreign key (connector_id)
    references connector;


alter table certificate add column compliance_status varchar;
alter table certificate add column compliance_result json;