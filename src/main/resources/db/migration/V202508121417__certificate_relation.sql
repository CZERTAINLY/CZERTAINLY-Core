CREATE TABLE certificate_relation (
    certificate_uuid UUID NOT NULL,
    source_certificate_uuid UUID NOT NULL,
    relation_type TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_certificate_relation PRIMARY KEY (certificate_uuid, source_certificate_uuid),

    CONSTRAINT fk_certificate_relation_certificate
        FOREIGN KEY (certificate_uuid)
        REFERENCES certificate (uuid)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_certificate_relation_source_certificate
        FOREIGN KEY (source_certificate_uuid)
        REFERENCES certificate (uuid)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
