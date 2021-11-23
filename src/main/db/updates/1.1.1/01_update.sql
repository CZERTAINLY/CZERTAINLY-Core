alter table ra_profile add column send_notifications boolean not null default false;
alter table ra_profile add column key_recoverable boolean not null default false;