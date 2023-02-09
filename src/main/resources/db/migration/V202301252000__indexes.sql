create unique index certificate_certificate_content_id_index
    on core.certificate (certificate_content_id);

create index certificate_ra_profile_uuid_index
    on core.certificate (ra_profile_uuid);

create index connector_2_function_group_connector_uuid_index
    on core.connector_2_function_group(connector_uuid);

create index connector_2_function_group_function_group_uuid_index
    on core.connector_2_function_group(function_group_uuid);

create index ra_profile_authority_instance_ref_uuid_index
    on core.ra_profile(authority_instance_ref_uuid);

create unique index authority_instance_reference_authority_instance_uuid_uindex
    on core.authority_instance_reference(authority_instance_uuid);

create index endpoint_function_group_uuid_index
    on core.endpoint(function_group_uuid);