create table notification_instance_reference (
    uuid uuid primary key,
    notification_instance_uuid uuid unique not null,
    name text not null check (name <> ''),
    description text default null,
    kind text default null,
    connector_uuid uuid references connector(uuid) default null,
    connector_name text default null
);

create table notification_instance_mapped_attributes (
    uuid uuid primary key,
    notification_instance_ref_uuid uuid references notification_instance_reference(uuid) on delete cascade not null,
    attribute_definition_uuid uuid references attribute_definition(uuid) on delete cascade not null,
    mapping_attribute_uuid uuid default null,
    mapping_attribute_name text default null
);
