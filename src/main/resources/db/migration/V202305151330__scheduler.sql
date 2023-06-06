CREATE TABLE scheduled_job
(
    uuid            UUID    NOT NULL,
    job_name        VARCHAR NOT NULL,
    cron_expression VARCHAR NOT NULL,
    object_data     JSONB,
    user_uuid       UUID,
    enabled         BOOLEAN NOT NULL,
    one_shot_only   BOOLEAN NOT NULL,
    system          BOOLEAN NOT NULL,
    job_class_name  VARCHAR NOT NULL
);

CREATE TABLE scheduled_job_history
(
    uuid                       UUID      NOT NULL,
    job_name                   VARCHAR   NOT NULL,
    job_execution              TIMESTAMP NOT NULL,
    job_end_time               TIMESTAMP,
    scheduler_execution_status VARCHAR   NOT NULL
)
