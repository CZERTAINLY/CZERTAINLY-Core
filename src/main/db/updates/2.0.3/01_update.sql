ALTER TABLE IF EXISTS ca_instance_reference
    RENAME TO authority_instance_reference;

ALTER TABLE authority_instance_reference
    RENAME COLUMN ca_instance_id TO authority_instance_uuid;

ALTER TABLE ra_profile
    RENAME COLUMN ca_instance_ref_id TO authority_instance_ref_id;

ALTER TABLE ra_profile
    RENAME COLUMN ca_instance_name TO authority_instance_name;

ALTER TABLE authority_instance_reference
    ALTER COLUMN authority_instance_uuid TYPE varchar (255);

ALTER SEQUENCE IF EXISTS ca_instance_reference_id_seq RENAME TO authority_instance_reference_id_seq ;
ALTER SEQUENCE IF EXISTS certificate_group_id_seq RENAME TO group_id_seq ;
ALTER SEQUENCE IF EXISTS certificate_entity_id_seq RENAME TO entity_id_seq ;

ALTER TABLE IF EXISTS certificate ALTER COLUMN common_name DROP NOT NULL;

ALTER TABLE IF EXISTS discovery_certificate ALTER COLUMN common_name DROP NOT NULL;

ALTER TABLE discovery_history
    RENAME COLUMN connector_id TO connector_uuid;

ALTER TABLE discovery_history
    ALTER COLUMN connector_uuid TYPE varchar (255);

ALTER TABLE authority_instance_reference
    RENAME COLUMN type TO kind;

ALTER TABLE credential
    RENAME COLUMN type TO kind;

ALTER TABLE discovery_history
    RENAME COLUMN type TO kind;


UPDATE function_group SET code = 'AUTHORITY_PROVIDER', name = 'authorityProvider' where uuid = '736b0fd6-5ea0-4e10-abe7-cfed39cc2a1a';
UPDATE function_group SET code = 'LEGACY_AUTHORITY_PROVIDER', name = 'legacyAuthorityProvider' where uuid = '435ee47f-fd03-4c50-ae6f-ca60f4829023';

UPDATE endpoint SET context = replace(context, '{authorityId}', '{uuid}');
UPDATE endpoint SET context = replace(context, '/caConnector/', '/authorityProvider/');
UPDATE endpoint SET name = replace(name, 'CAInstance', 'AuthorityInstance');

UPDATE endpoint SET context = '/v1/authorityProvider/authorities/{uuid}/raProfile/attributes' where uuid = 'e43155b6-51ad-46e0-a60c-176ee5e6dfea';
UPDATE endpoint SET context = '/v2/authorityProvider/authorities/{uuid}/certificates/renew' where uuid = 'efdb9bcd-4f7c-473b-8704-77b12b3f6d33';
UPDATE endpoint SET context = '/v2/authorityProvider/authorities/{uuid}/certificates/revoke' where uuid = 'd9e162ae-2d50-4e98-bc37-62d015c43199';
UPDATE endpoint SET context = '/v2/authorityProvider/authorities/{uuid}/certificates/revoke' where uuid = '7085fad6-df6e-4697-9c8e-7c80c2a12bd7';


UPDATE credential
    SET attributes = REPLACE(attributes,'{"id":', '{"uuid":');

UPDATE discovery_history
    SET attributes = REPLACE(attributes,'{"id":', '{"uuid":');

UPDATE ra_profile
    SET attributes = REPLACE(attributes,'{"id":', '{"uuid":');

ALTER TABLE discovery_certificate
    RENAME COLUMN IF EXISTS discovery_uuid TO discovery_id;