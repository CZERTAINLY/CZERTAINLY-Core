alter table certificate
    add column issue_attributes text default null,
    add column revoke_attributes text default null;