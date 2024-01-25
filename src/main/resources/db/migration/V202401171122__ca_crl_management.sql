CREATE TABLE crl (
	uuid UUID NOT NULL,
	ca_certificate_uuid UUID,
	issuer_dn VARCHAR NOT NULL,
	serial_number VARCHAR NOT NULL,
	crl_issuer_dn VARCHAR NOT NULL,
    crl_number VARCHAR NOT NULL,
    next_update TIMESTAMP NOT NULL,
    crl_number_delta VARCHAR,
    next_update_delta TIMESTAMP,
    last_revocation_date TIMESTAMP,
	PRIMARY KEY (uuid),
	FOREIGN KEY (ca_certificate_uuid) REFERENCES certificate(uuid)
);

CREATE TABLE crl_entry (
	crl_uuid UUID,
	serial_number VARCHAR,
    revocation_date TIMESTAMP NOT NULL,
    revocation_reason VARCHAR NOT NULL,
	PRIMARY KEY (crl_uuid, serial_number),
	FOREIGN KEY (crl_uuid) REFERENCES crl(uuid) ON UPDATE CASCADE ON DELETE CASCADE
);

