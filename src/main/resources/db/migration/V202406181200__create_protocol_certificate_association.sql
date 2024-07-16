CREATE TABLE certificate_protocol_association (
	uuid UUID NOT NULL,
	certificate_uuid UUID NOT NULL REFERENCES certificate(uuid) on delete cascade,
	protocol TEXT NOT NULL,
	protocol_profile_uuid UUID NOT NULL,
	additional_protocol_uuid UUID,
	PRIMARY KEY (uuid)
);
