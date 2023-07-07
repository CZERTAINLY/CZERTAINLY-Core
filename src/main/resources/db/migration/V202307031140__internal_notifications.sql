create table notification (
    uuid uuid primary key,
    message text not null check (message <> ''),
    detail text default null
);

create table notification_recipient (
    uuid uuid primary key,
    notification_uuid uuid references notification(uuid) on delete cascade not null,
    user_uuid uuid not null,
    read_at timestamp default null,
    unique (notification_uuid, user_uuid)
);