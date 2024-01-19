CREATE TABLE crl (
	uuid UUID NOT NULL UNIQUE,
	ca_certificate_uuid UUID,
	issuer_dn VARCHAR,
	serial_number VARCHAR,
	crl_issuer_dn VARCHAR,
    crl_number VARCHAR,
    next_update TIMESTAMP,
    crl_number_delta VARCHAR,
    next_update_delta TIMESTAMP,
    last_revocation_date TIMESTAMP,
	PRIMARY KEY (uuid),
	FOREIGN KEY (ca_certificate_uuid) REFERENCES certificate(uuid)
);

CREATE TABLE crl_entry (
	crl_uuid UUID,
	serial_number VARCHAR,
    revocation_date TIMESTAMP,
    revocation_reason VARCHAR,
	PRIMARY KEY (crl_uuid, serial_number),
	FOREIGN KEY (crl_uuid) REFERENCES crl(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

