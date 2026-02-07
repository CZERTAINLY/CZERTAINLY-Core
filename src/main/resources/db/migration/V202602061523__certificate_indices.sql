create index certificate_signature_algorithm
    on certificate (signature_algorithm);

create index certificate_alt_signature_algorithm
    on certificate (alt_signature_algorithm);

create index certificate_public_key_algorithm
    on certificate (public_key_algorithm);

create index certificate_alt_public_key_algorithm
    on certificate (alt_public_key_algorithm);

create index certificate_key_size
    on certificate (key_size);

create index certificate_alt_key_size
    on certificate (alt_key_size);
