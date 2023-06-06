alter table certificate_key_group add column email text default null;
alter table certificate_key_group rename to "group";