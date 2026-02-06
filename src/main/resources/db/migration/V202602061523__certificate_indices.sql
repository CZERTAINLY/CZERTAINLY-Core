create index certificate_signature_algorithm
    on core.certificate (signature_algorithm);

create index certificate_alt_signature_algorithm
    on core.certificate (alt_signature_algorithm);

create index certificate_public_key_algorithm
    on core.certificate (public_key_algorithm);

create index certificate_alt_public_key_algorithm
    on core.certificate (alt_public_key_algorithm);

create index certificate_key_size
    on core.certificate (key_size);

create index certificate_alt_key_size
    on core.certificate (alt_key_size);
