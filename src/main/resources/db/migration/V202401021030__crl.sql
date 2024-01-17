
CREATE TABLE crl (
	id BIGINT NOT NULL,
	uuid UUID NOT NULL UNIQUE,
	ca_certificate_uuid UUID DEFAULT NULL,
	issuer_dn VARCHAR UNIQUE,
	serial_number VARCHAR UNIQUE,
	crl_issuer_dn VARCHAR,
    crl_number VARCHAR,
    next_update TIMESTAMP,
    crl_number_delta VARCHAR,
    next_update_delta TIMESTAMP,
    last_revocation_date TIMESTAMP,
	PRIMARY KEY (id),
	FOREIGN KEY (ca_certificate_uuid) REFERENCES certificate(uuid)
);

CREATE TABLE crl_entry (
	id BIGINT NOT NULL,
	crl_uuid UUID,
	serial_number VARCHAR,
    revocation_date TIMESTAMP,
    revocation_reason VARCHAR,
	PRIMARY KEY (id),
	FOREIGN KEY (crl_uuid) REFERENCES crl(uuid)
);
;