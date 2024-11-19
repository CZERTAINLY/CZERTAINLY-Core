DROP SEQUENCE audit_log_id_seq;
DROP TABLE audit_log;

CREATE sequence audit_log_id_seq start 1 increment 1;
CREATE TABLE audit_log (
    id BIGINT NOT NULL,
    version TEXT NOT NULL,
    logged_at TIMESTAMP NOT NULL,
    "module" TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    actor_auth_method TEXT NOT NULL,
    actor_uuid UUID,
    actor_name TEXT,
    resource TEXT NOT NULL,
    affiliated_resource TEXT,
--    resource_uuids TEXT,
--    resource_names TEXT,
    operation TEXT NOT NULL,
    operation_result TEXT NOT NULL,
    message TEXT,
    log_record JSONB NOT NULL,
    PRIMARY KEY (id)
);