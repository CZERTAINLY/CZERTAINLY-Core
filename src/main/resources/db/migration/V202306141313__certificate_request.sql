CREATE TABLE certificate_request
(
    uuid                       UUID      NOT NULL,
    common_name                VARCHAR   NOT NULL,
    i_author                   VARCHAR   NULL DEFAULT NULL,
    i_cre                      TIMESTAMP NOT NULL,
    i_upd                      TIMESTAMP NOT NULL,
    subject_dn                 VARCHAR   NOT NULL,
    public_key_algorithm       VARCHAR   NOT NULL,
    signature_algorithm        VARCHAR   NOT NULL,
    fingerprint                VARCHAR   NOT NULL,
    subject_alternative_names  TEXT      NULL DEFAULT NULL,
    key_usage                  TEXT      NULL DEFAULT '',
    certificate_type           VARCHAR   NOT NULL,
    content                    TEXT      NOT NULL,
    certificate_request_format VARCHAR   NOT NULL,
    attributes                 TEXT      NULL DEFAULT NULL,
    signature_attributes       TEXT      NULL DEFAULT NULL,
    PRIMARY KEY ("uuid")
);


ALTER TABLE certificate
    ADD COLUMN certificate_request_uuid uuid NULL DEFAULT NULL;
ALTER TABLE certificate
    ADD FOREIGN KEY (certificate_request_uuid) REFERENCES certificate_request (uuid);

