UPDATE token_instance_reference SET status = 'DEACTIVATED' WHERE uuid IN
    (SELECT ti.uuid from token_instance_reference ti JOIN cryptographic_key ck ON ti.uuid = ck.token_instance_uuid
    JOIN cryptographic_key_item cki ON cki.key_uuid = ck.uuid WHERE cki.key_algorithm IN ('DILITHIUM', 'SPHINCSPLUS'));
