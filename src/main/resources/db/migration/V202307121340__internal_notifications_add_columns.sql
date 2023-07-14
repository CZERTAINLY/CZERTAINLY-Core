alter table notification
    add column sent_at timestamp not null default CURRENT_TIMESTAMP,
    add column target_object_type text default null,
    add column target_object_identification text default null;
