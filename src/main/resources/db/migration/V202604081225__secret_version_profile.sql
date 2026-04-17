ALTER TABLE secret_version
    ADD COLUMN vault_profile_uuid uuid,
    ADD FOREIGN KEY (vault_profile_uuid) REFERENCES vault_profile(uuid) ON DELETE SET NULL ON UPDATE CASCADE;

UPDATE secret_version sv SET vault_profile_uuid = (
    SELECT s.source_vault_profile_uuid
    FROM secret s
    WHERE s.latest_version_uuid = sv.uuid
);

ALTER TABLE secret_version DROP COLUMN vault_instance_uuid;
