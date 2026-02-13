create index certificate_validation_status
    on certificate (validation_status);

create index certificate_archived
    on certificate (archived);

create index certificate_key_uuid
    on certificate (key_uuid);
