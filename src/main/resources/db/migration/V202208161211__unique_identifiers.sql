-- drop all the foreign key constraints

alter table acme_account drop CONSTRAINT acme_account_to_ra_profile_key;
alter table acme_account drop CONSTRAINT	acme_profile_to_acme_account_key;
alter table	acme_authorization	drop CONSTRAINT	acme_authorization_to_order_key;
alter table	acme_challenge	drop CONSTRAINT	acme_challenge_to_authorization_key;
alter table	acme_order	drop CONSTRAINT	acme_order_to_account_key;
alter table	acme_order	drop CONSTRAINT	acme_order_to_certificate_key;
alter table	acme_profile	drop CONSTRAINT	acme_profile_to_ra_profile_key;
alter table	admin	drop CONSTRAINT	fkrq5yjsxacu7105ihrfcp662xe;
alter table	authority_instance_reference	drop CONSTRAINT	fk2t7xntc30lq9crkgdfntk6hsh;
alter table	certificate	drop CONSTRAINT	fk41nap2d0f529tuabyjs424a80;
alter table	certificate	drop CONSTRAINT	fkcuayu1tjhuojrg3c28i2uqi4g;
alter table	certificate_event_history	drop CONSTRAINT	certificate_history_to_certificate_key_1;
alter table	certificate_location	drop CONSTRAINT	certificate_location_to_certificate_key;
alter table	certificate_location	drop CONSTRAINT	certificate_location_to_location_key;
alter table	client	drop CONSTRAINT	fklyok3m28dnf8lr2gevt5aj2np;
alter table	client_authorization	drop CONSTRAINT	fkdn5d25h79l2el4iv9w7isnnjc;
alter table	client_authorization	drop CONSTRAINT	fkkjs42uvpsdos793wflp2onl3s;
alter table	compliance_group	drop CONSTRAINT	compliance_group_to_connector;
alter table	compliance_profile_2_compliance_group	drop CONSTRAINT	compliance_group_to_mapping_key;
alter table	ra_profile_2_compliance_profile	drop CONSTRAINT	compliance_profile_to_mapping_key;
alter table	compliance_profile_2_compliance_group	drop CONSTRAINT	compliance_profile_to_mapping_key;
alter table	compliance_profile_rule	drop CONSTRAINT	compliance_profile_rule_to_compliance_profile;
alter table	compliance_profile_rule	drop CONSTRAINT	compliance_profile_rule_to_compliance_rule;
alter table	compliance_rule	drop CONSTRAINT	compliance_rule_to_compliance_group_key;
alter table	compliance_rule	drop CONSTRAINT	compliance_rule_to_connector_key;
alter table	connector_2_function_group	drop CONSTRAINT	fk1qvna5aqsvmfwsxr90q9ewsk3;
alter table	connector_2_function_group	drop CONSTRAINT	fke04tlwcpn0a6otrw84gke8k3d;
alter table	credential	drop CONSTRAINT	fkrxdkw4wef9tt0fbx5t892wv59;
alter table	discovery_certificate	drop CONSTRAINT	fk4uptmj2ejf9i1cfjnikmesa5p;
alter table	endpoint	drop CONSTRAINT	fkgj4l79prijfj4nnjl7idi27be;
alter table	entity_instance_reference	drop CONSTRAINT	entity_instance_to_connector_key;
alter table	location	drop CONSTRAINT	location_to_entity_instance_reference_key;
alter table	ra_profile	drop CONSTRAINT	fk1ybgp06wf8uoegwfhsg4fev2a;
alter table	ra_profile	drop CONSTRAINT	ra_profile_to_acme_profile_key;
alter table	ra_profile_2_compliance_profile	drop CONSTRAINT	ra_profile_to_compliance_mapping_key;

-- drop all the primary keys in the tables that uses Id

alter table acme_authorization drop CONSTRAINT acme_authorization_pkey;
alter table acme_challenge drop CONSTRAINT acme_challenge_pkey;
alter table acme_order drop CONSTRAINT acme_order_pkey;
alter table acme_profile drop CONSTRAINT acme_profile_pkey;
alter table admin drop CONSTRAINT admin_pkey;
alter table authority_instance_reference drop CONSTRAINT authority_instance_reference_pkey;
alter table certificate drop CONSTRAINT certificate_pkey;
alter table certificate_event_history drop CONSTRAINT certificate_event_history_pkey;
alter table certificate_group drop CONSTRAINT certificate_group_pkey;
alter table certificate_location drop CONSTRAINT certificate_location_pkey;
alter table client drop CONSTRAINT client_pkey;
alter table compliance_group drop CONSTRAINT compliance_group_pkey;
alter table compliance_profile drop CONSTRAINT compliance_profile_pkey;
alter table compliance_profile_rule drop CONSTRAINT compliance_profile_rule_pkey;
alter table compliance_rule drop CONSTRAINT compliance_rule_pkey;
alter table connector drop CONSTRAINT connector_pkey;
alter table credential drop CONSTRAINT credential_pkey;
alter table endpoint drop CONSTRAINT endpoint_pkey;
alter table entity_instance_reference drop CONSTRAINT entity_instance_reference_pkey;
alter table function_group drop CONSTRAINT function_group_pkey;
alter table location drop CONSTRAINT location_pkey;
alter table ra_profile drop CONSTRAINT ra_profile_pkey;
alter table acme_account drop CONSTRAINT acme_account_pkey;
alter table discovery_certificate drop CONSTRAINT discovery_certificate_pkey;
alter table discovery_history drop CONSTRAINT discovery_history_pkey;
alter table client_authorization drop CONSTRAINT client_authorization_pkey;

-- Add uuid as primary keys in the tables

alter table acme_authorization ADD PRIMARY KEY (uuid);
alter table acme_challenge ADD PRIMARY KEY (uuid);
alter table acme_order ADD PRIMARY KEY (uuid);
alter table acme_profile ADD PRIMARY KEY (uuid);
alter table admin ADD PRIMARY KEY (uuid);
alter table authority_instance_reference ADD PRIMARY KEY (uuid);
alter table certificate ADD PRIMARY KEY (uuid);
alter table certificate_event_history ADD PRIMARY KEY (uuid);
alter table certificate_group ADD PRIMARY KEY (uuid);
alter table client ADD PRIMARY KEY (uuid);
alter table compliance_group ADD PRIMARY KEY (uuid);
alter table compliance_profile ADD PRIMARY KEY (uuid);
alter table compliance_profile_rule ADD PRIMARY KEY (uuid);
alter table compliance_rule ADD PRIMARY KEY (uuid);
alter table connector ADD PRIMARY KEY (uuid);
alter table credential ADD PRIMARY KEY (uuid);
alter table endpoint ADD PRIMARY KEY (uuid);
alter table entity_instance_reference ADD PRIMARY KEY (uuid);
alter table function_group ADD PRIMARY KEY (uuid);
alter table location ADD PRIMARY KEY (uuid);
alter table ra_profile ADD PRIMARY KEY (uuid);
alter table acme_account ADD PRIMARY KEY (uuid);
alter table discovery_history ADD PRIMARY KEY (uuid);
alter table discovery_certificate ADD PRIMARY KEY (uuid);


-- Update uuid clumn to type UUID

alter table acme_authorization ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table acme_challenge ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table acme_order ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table acme_profile ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table admin ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table authority_instance_reference ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table certificate ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table certificate_event_history ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table certificate_group ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table client ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table compliance_group ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table compliance_profile ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table compliance_profile_rule ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table compliance_rule ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table connector ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table credential ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table endpoint ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table entity_instance_reference ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table function_group ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table location ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table ra_profile ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table acme_account ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table discovery_history ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);
alter table discovery_certificate ALTER COLUMN uuid TYPE uuid USING (uuid::uuid);

-- Update the foreign key references to UUID

ALTER TABLE acme_account ADD COLUMN if NOT EXISTS ra_profile_uuid uuid;
UPDATE acme_account SET ra_profile_uuid = ra_profile.uuid FROM ra_profile WHERE ra_profile.id = acme_account.ra_profile_id;
ALTER TABLE acme_account DROP COLUMN ra_profile_id;

ALTER TABLE acme_account ADD COLUMN if NOT EXISTS acme_profile_uuid uuid;
UPDATE acme_account SET acme_profile_uuid = acme_profile.uuid FROM acme_profile WHERE acme_profile.id = acme_account.acme_profile_id;
ALTER TABLE acme_account DROP COLUMN acme_profile_id;

ALTER TABLE acme_authorization ADD COLUMN if NOT EXISTS order_uuid uuid;
UPDATE acme_authorization SET order_uuid = acme_order.uuid FROM acme_order WHERE acme_order.id = acme_authorization.order_id;
ALTER TABLE acme_authorization DROP COLUMN order_id;

ALTER TABLE acme_challenge ADD COLUMN if NOT EXISTS authorization_uuid uuid;
UPDATE acme_challenge SET authorization_uuid = acme_authorization.uuid FROM acme_authorization WHERE acme_authorization.id = acme_challenge.authorization_id;
ALTER TABLE acme_challenge DROP COLUMN authorization_id;

ALTER TABLE acme_order ADD COLUMN if NOT EXISTS account_uuid uuid;
UPDATE acme_order SET account_uuid = acme_account.uuid FROM acme_account WHERE acme_account.id = acme_order.account_id;
ALTER TABLE acme_order DROP COLUMN account_id;

ALTER TABLE acme_order ADD COLUMN if NOT EXISTS _certificate_ref uuid;
UPDATE acme_order SET _certificate_ref = certificate.uuid FROM certificate WHERE certificate.id = acme_order.certificate_ref;
ALTER TABLE acme_order DROP COLUMN certificate_ref;
ALTER TABLE acme_order RENAME COLUMN _certificate_ref to certificate_ref;

ALTER TABLE acme_profile ADD COLUMN if NOT EXISTS ra_profile_uuid uuid;
UPDATE acme_profile SET ra_profile_uuid = ra_profile.uuid FROM ra_profile WHERE ra_profile.id = acme_profile.ra_profile_id;
ALTER TABLE acme_profile DROP COLUMN ra_profile_id;

ALTER TABLE admin ADD COLUMN if NOT EXISTS certificate_uuid uuid;
UPDATE admin SET certificate_uuid = certificate.uuid FROM certificate WHERE certificate.id = admin.certificate_id;
ALTER TABLE admin DROP COLUMN certificate_id;

ALTER TABLE authority_instance_reference ADD COLUMN if NOT EXISTS connector_uuid uuid;
UPDATE authority_instance_reference SET connector_uuid = connector.uuid FROM connector WHERE connector.id = authority_instance_reference.connector_id;
ALTER TABLE authority_instance_reference DROP COLUMN connector_id;

ALTER TABLE certificate ADD COLUMN if NOT EXISTS ra_profile_uuid uuid;
UPDATE certificate SET ra_profile_uuid = ra_profile.uuid FROM ra_profile WHERE ra_profile.id = certificate.ra_profile_id;
ALTER TABLE certificate DROP COLUMN ra_profile_id;

ALTER TABLE certificate ADD COLUMN if NOT EXISTS group_uuid uuid;
UPDATE certificate SET group_uuid = certificate_group.uuid FROM certificate_group WHERE certificate_group.id = certificate.group_id;
ALTER TABLE certificate DROP COLUMN group_id;

ALTER TABLE certificate_event_history ADD COLUMN if NOT EXISTS certificate_uuid uuid;
UPDATE certificate_event_history SET certificate_uuid = certificate.uuid FROM certificate WHERE certificate.id = certificate_event_history.certificate_id;
ALTER TABLE certificate_event_history DROP COLUMN certificate_id;

ALTER TABLE certificate_location ADD COLUMN if NOT EXISTS location_uuid uuid;
UPDATE certificate_location SET location_uuid = location.uuid FROM location WHERE location.id = certificate_location.location_id;
ALTER TABLE certificate_location DROP COLUMN location_id;

ALTER TABLE certificate_location ADD COLUMN if NOT EXISTS certificate_uuid uuid;
UPDATE certificate_location SET certificate_uuid = certificate.uuid FROM certificate WHERE certificate.id = certificate_location.certificate_id;
ALTER TABLE certificate_location DROP COLUMN certificate_id;

ALTER TABLE client ADD COLUMN if NOT EXISTS certificate_uuid uuid;
UPDATE client SET certificate_uuid = certificate.uuid FROM certificate WHERE certificate.id = client.certificate_id;
ALTER TABLE client DROP COLUMN certificate_id;

ALTER TABLE client_authorization ADD COLUMN if NOT EXISTS ra_profile_uuid uuid;
UPDATE client_authorization SET ra_profile_uuid = ra_profile.uuid FROM ra_profile WHERE ra_profile.id = client_authorization.ra_profile_id;
ALTER TABLE client_authorization DROP COLUMN ra_profile_id;

ALTER TABLE client_authorization ADD COLUMN if NOT EXISTS client_uuid uuid;
UPDATE client_authorization SET client_uuid = client.uuid FROM client WHERE client.id = client_authorization.client_id;
ALTER TABLE client_authorization DROP COLUMN client_id;

ALTER TABLE compliance_group ADD COLUMN if NOT EXISTS connector_uuid uuid;
UPDATE compliance_group SET connector_uuid = connector.uuid FROM connector WHERE connector.id = compliance_group.connector_id;
ALTER TABLE compliance_group DROP COLUMN connector_id;

ALTER TABLE compliance_profile_2_compliance_group ADD COLUMN if NOT EXISTS profile_uuid uuid;
UPDATE compliance_profile_2_compliance_group SET profile_uuid = compliance_profile.uuid FROM compliance_profile WHERE compliance_profile.id = compliance_profile_2_compliance_group.profile_id;
ALTER TABLE compliance_profile_2_compliance_group DROP COLUMN profile_id;

ALTER TABLE compliance_profile_2_compliance_group ADD COLUMN if NOT EXISTS group_uuid uuid;
UPDATE compliance_profile_2_compliance_group SET group_uuid = compliance_group.uuid FROM compliance_group WHERE compliance_group.id = compliance_profile_2_compliance_group.group_id;
ALTER TABLE compliance_profile_2_compliance_group DROP COLUMN group_id;

ALTER TABLE compliance_profile_rule ADD COLUMN if NOT EXISTS compliance_profile_uuid uuid;
UPDATE compliance_profile_rule SET compliance_profile_uuid = compliance_profile.uuid FROM compliance_profile WHERE compliance_profile.id = compliance_profile_rule.compliance_profile_id;
ALTER TABLE compliance_profile_rule DROP COLUMN compliance_profile_id;

ALTER TABLE compliance_profile_rule ADD COLUMN if NOT EXISTS rule_uuid uuid;
UPDATE compliance_profile_rule SET rule_uuid = compliance_rule.uuid FROM compliance_rule WHERE compliance_rule.id = compliance_profile_rule.rule_id;
ALTER TABLE compliance_profile_rule DROP COLUMN rule_id;

ALTER TABLE compliance_rule ADD COLUMN if NOT EXISTS group_uuid uuid;
UPDATE compliance_rule SET group_uuid = compliance_group.uuid FROM compliance_group WHERE compliance_group.id = compliance_rule.group_id;
ALTER TABLE compliance_rule DROP COLUMN group_id;

ALTER TABLE compliance_rule ADD COLUMN if NOT EXISTS connector_uuid uuid;
UPDATE compliance_rule SET connector_uuid = connector.uuid FROM connector WHERE connector.id = compliance_rule.connector_id;
ALTER TABLE compliance_rule DROP COLUMN connector_id;

ALTER TABLE connector_2_function_group ADD COLUMN if NOT EXISTS connector_uuid uuid;
UPDATE connector_2_function_group SET connector_uuid = connector.uuid FROM connector WHERE connector.id = connector_2_function_group.connector_id;
ALTER TABLE connector_2_function_group DROP COLUMN connector_id;

ALTER TABLE connector_2_function_group ADD COLUMN if NOT EXISTS function_group_uuid uuid;
UPDATE connector_2_function_group SET function_group_uuid = function_group.uuid FROM function_group WHERE function_group.id = connector_2_function_group.function_group_id;
ALTER TABLE connector_2_function_group DROP COLUMN function_group_id;

ALTER TABLE credential ADD COLUMN if NOT EXISTS connector_uuid uuid;
UPDATE credential SET connector_uuid = connector.uuid FROM connector WHERE connector.id = credential.connector_id;
ALTER TABLE credential DROP COLUMN connector_id;

ALTER TABLE discovery_certificate ADD COLUMN if NOT EXISTS discovery_uuid uuid;
UPDATE discovery_certificate SET discovery_uuid = discovery_history.uuid FROM discovery_history WHERE discovery_history.id = discovery_certificate.discovery_id;
ALTER TABLE discovery_certificate DROP COLUMN discovery_id;

ALTER TABLE endpoint ADD COLUMN if NOT EXISTS function_group_uuid uuid;
UPDATE endpoint SET function_group_uuid = function_group.uuid FROM function_group WHERE function_group.id = endpoint.function_group_id;
ALTER TABLE endpoint DROP COLUMN function_group_id;

ALTER TABLE entity_instance_reference ADD COLUMN if NOT EXISTS connector_uuid uuid;
UPDATE entity_instance_reference SET connector_uuid = connector.uuid FROM connector WHERE connector.id = entity_instance_reference.connector_id;
ALTER TABLE entity_instance_reference DROP COLUMN connector_id;

ALTER TABLE location ADD COLUMN if NOT EXISTS entity_instance_ref_uuid uuid;
UPDATE location SET entity_instance_ref_uuid = entity_instance_reference.uuid FROM entity_instance_reference WHERE entity_instance_reference.id = location.entity_instance_ref_id;
ALTER TABLE location DROP COLUMN entity_instance_ref_id;

ALTER TABLE ra_profile ADD COLUMN if NOT EXISTS acme_profile_uuid uuid;
UPDATE ra_profile SET acme_profile_uuid = acme_profile.uuid FROM acme_profile WHERE acme_profile.id = ra_profile.acme_profile_id;
ALTER TABLE ra_profile DROP COLUMN acme_profile_id;

ALTER TABLE ra_profile ADD COLUMN if NOT EXISTS authority_instance_ref_uuid uuid;
UPDATE ra_profile SET authority_instance_ref_uuid = authority_instance_reference.uuid FROM authority_instance_reference WHERE authority_instance_reference.id = ra_profile.authority_instance_ref_id;
ALTER TABLE ra_profile DROP COLUMN authority_instance_ref_id;

ALTER TABLE ra_profile_2_compliance_profile ADD COLUMN if NOT EXISTS ra_profile_uuid uuid;
UPDATE ra_profile_2_compliance_profile SET ra_profile_uuid = ra_profile.uuid FROM ra_profile WHERE ra_profile.id = ra_profile_2_compliance_profile.ra_profile_id;
ALTER TABLE ra_profile_2_compliance_profile DROP COLUMN ra_profile_id;

ALTER TABLE ra_profile_2_compliance_profile ADD COLUMN if NOT EXISTS compliance_profile_uuid uuid;
UPDATE ra_profile_2_compliance_profile SET compliance_profile_uuid = compliance_profile.uuid FROM compliance_profile WHERE compliance_profile.id = ra_profile_2_compliance_profile.compliance_profile_id;
ALTER TABLE ra_profile_2_compliance_profile DROP COLUMN compliance_profile_id;


-- delete column id from all the tables

alter table acme_account DROP COLUMN id;
alter table acme_authorization DROP COLUMN id;
alter table acme_challenge DROP COLUMN id;
alter table acme_order DROP COLUMN id;
alter table acme_profile DROP COLUMN id;
alter table admin DROP COLUMN id;
alter table authority_instance_reference DROP COLUMN id;
alter table certificate DROP COLUMN id;
alter table certificate_event_history DROP COLUMN id;
alter table certificate_group DROP COLUMN id;
alter table client DROP COLUMN id;
alter table compliance_group DROP COLUMN id;
alter table compliance_profile DROP COLUMN id;
alter table compliance_profile_rule DROP COLUMN id;
alter table compliance_rule DROP COLUMN id;
alter table connector DROP COLUMN id;
alter table credential DROP COLUMN id;
alter table endpoint DROP COLUMN id;
alter table entity_instance_reference DROP COLUMN id;
alter table function_group DROP COLUMN id;
alter table location DROP COLUMN id;
alter table ra_profile DROP COLUMN id;
alter table discovery_history DROP COLUMN id;
alter table discovery_certificate DROP COLUMN id;


-- Add the constrains back to the database using the new columns

alter table if exists admin
    add constraint admin_to_certificate_key
    foreign key (certificate_uuid)
    references certificate;

alter table if exists authority_instance_reference
    add constraint authority_instance_reference_to_connector_key
    foreign key (connector_uuid)
    references connector;


alter table if exists certificate
    add constraint certificate_to_certificate_group_key
    foreign key (group_uuid)
    references certificate_group;

alter table if exists certificate
    add constraint certificate_to_ra_profile_key
    foreign key (ra_profile_uuid)
    references ra_profile;

alter table if exists client
    add constraint client_to_certificate_key
    foreign key (certificate_uuid)
    references certificate;

alter table if exists client_authorization
    add constraint client_authorization_to_client_key
    foreign key (client_uuid)
    references client;

alter table if exists client_authorization
    add constraint client_authorization_to_ra_profile_key
    foreign key (ra_profile_uuid)
    references ra_profile;

alter table if exists connector_2_function_group
    add constraint connector_function_group_to_connector_key
    foreign key (connector_uuid)
    references connector;

alter table if exists connector_2_function_group
    add constraint connector_2_function_group_to_function_group_key
    foreign key (function_group_uuid)
    references function_group;

alter table if exists credential
    add constraint credential_to_connector_key
    foreign key (connector_uuid)
    references connector;


alter table if exists discovery_certificate
    add constraint discovery_certificate_to_discovery_history_key
    foreign key (discovery_uuid)
    references discovery_history;

alter table if exists endpoint
    add constraint end_point_to_function_group_key
    foreign key (function_group_uuid)
    references function_group;

alter table if exists ra_profile
    add constraint ra_profile_to_authority_instance_reference
    foreign key (authority_instance_ref_uuid)
    references authority_instance_reference;

alter table if exists acme_account
    add constraint acme_account_to_ra_profile_key
    foreign key (ra_profile_uuid)
    references ra_profile
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_account
    add constraint acme_profile_to_acme_account_key
    foreign key (acme_profile_uuid)
    references acme_profile
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_authorization
    add constraint acme_authorization_to_order_key
    foreign key (order_uuid)
    references acme_order
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_challenge
    add constraint acme_challenge_to_authorization_key
    foreign key (authorization_uuid)
    references acme_authorization
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_order
    add constraint acme_order_to_account_key
    foreign key (account_uuid)
    references acme_account
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists acme_order
    add constraint acme_order_to_certificate_key
    foreign key (certificate_ref)
    references certificate
    ON UPDATE NO ACTION ON DELETE CASCADE;


alter table if exists acme_profile
    add constraint acme_profile_to_ra_profile_key
    foreign key (ra_profile_uuid)
    references ra_profile;

alter table if exists ra_profile
    add constraint ra_profile_to_acme_profile_key
    foreign key (acme_profile_uuid)
    references acme_profile;

alter table if exists certificate_event_history
    add constraint CERTIFICATE_HISTORY_TO_CERTIFICATE_KEY_1
    foreign key (certificate_uuid)
    references certificate
    ON UPDATE NO ACTION ON DELETE CASCADE;

alter table if exists entity_instance_reference
    add constraint entity_instance_to_connector_key
    foreign key (connector_uuid)
    references connector;

alter table if exists location
    add constraint location_to_entity_instance_reference_key
    foreign key (entity_instance_ref_uuid)
    references entity_instance_reference;

alter table if exists certificate_location
    add constraint certificate_location_to_location_key
    foreign key (location_uuid)
    references location;

alter table if exists certificate_location
    add constraint certificate_location_to_certificate_key
    foreign key (certificate_uuid)
    references certificate;

alter table if exists ra_profile_2_compliance_profile
    add constraint COMPLIANCE_PROFILE_TO_MAPPING_KEY
    foreign key (compliance_profile_uuid)
    references compliance_profile
    ON DELETE CASCADE;

alter table if exists ra_profile_2_compliance_profile
    add constraint RA_PROFILE_TO_COMPLIANCE_MAPPING_KEY
    foreign key (ra_profile_uuid)
    references ra_profile
    ON DELETE CASCADE;


alter table if exists compliance_profile_2_compliance_group
    add constraint COMPLIANCE_GROUP_TO_MAPPING_KEY
    foreign key (group_uuid)
    references compliance_group;

alter table if exists compliance_profile_2_compliance_group
    add constraint COMPLIANCE_PROFILE_TO_MAPPING_KEY
    foreign key (profile_uuid)
    references compliance_profile
    ON DELETE CASCADE;

alter table if exists compliance_group
    add constraint COMPLIANCE_GROUP_TO_CONNECTOR
    foreign key (connector_uuid)
    references connector ON DELETE CASCADE;

alter table if exists compliance_profile_rule
    add constraint COMPLIANCE_PROFILE_RULE_TO_COMPLIANCE_PROFILE
    foreign key (compliance_profile_uuid)
    references compliance_profile;

alter table if exists compliance_profile_rule
    add constraint COMPLIANCE_PROFILE_RULE_TO_COMPLIANCE_RULE
    foreign key (rule_uuid)
    references compliance_rule;


alter table if exists compliance_rule
    add constraint COMPLIANCE_RULE_TO_COMPLIANCE_GROUP_KEY
    foreign key (group_uuid)
    references compliance_group;

alter table if exists compliance_rule
    add constraint COMPLIANCE_RULE_TO_CONNECTOR_KEY
    foreign key (connector_uuid)
    references connector;



-- Drop all the existing sequences that are not used anymore

drop sequence admin_id_seq;
drop sequence authority_instance_reference_id_seq;
drop sequence group_id_seq;
drop sequence certificate_id_seq;
drop sequence client_id_seq;
drop sequence connector_id_seq;
drop sequence credential_id_seq;
drop sequence discovery_certificate_id_seq;
drop sequence discovery_id_seq;
drop sequence endpoint_id_seq;
drop sequence function_group_id_seq;
drop sequence ra_profile_id_seq;
drop sequence acme_new_account_id_seq;
drop sequence acme_new_authorization_id_seq;
drop sequence acme_new_challenge_id_seq;
drop sequence acme_new_order_id_seq;
drop sequence acme_profile_id_seq;
drop sequence certificate_event_history_id_seq;
drop sequence entity_instance_reference_id_seq;
drop sequence location_id_seq;
drop sequence compliance_profile_id_seq;
drop sequence compliance_rule_id_seq;
drop sequence compliance_group_id_seq;
drop sequence compliance_profile_rule_id_seq;