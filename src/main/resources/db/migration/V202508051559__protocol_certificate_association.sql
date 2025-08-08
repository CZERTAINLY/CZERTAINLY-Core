CREATE TABLE protocol_certificate_associations (
    uuid UUID PRIMARY KEY,
    owner_uuid UUID,
    group_uuids UUID[],
    custom_attributes JSONB
);

ALTER TABLE acme_profile ADD COLUMN certificate_associations_uuid UUID REFERENCES protocol_certificate_associations(uuid);
ALTER TABLE scep_profile ADD COLUMN certificate_associations_uuid UUID REFERENCES protocol_certificate_associations(uuid);
ALTER TABLE cmp_profile ADD COLUMN certificate_associations_uuid UUID REFERENCES protocol_certificate_associations(uuid);