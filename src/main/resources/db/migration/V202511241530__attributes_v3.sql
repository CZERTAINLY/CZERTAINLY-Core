ALTER TABLE attribute_definition ADD COLUMN version INT NOT NULL DEFAULT 2;
UPDATE attribute_definition SET version = 3 WHERE type = 'CUSTOM';

UPDATE attribute_definition ad
SET definition =
    jsonb_set(
        jsonb_set(ad.definition, '{version}', '3', false),
        '{content}',
        (
            SELECT jsonb_agg(
                elem || jsonb_build_object('contentType', LOWER(ad.content_type))
            )
            FROM jsonb_array_elements(ad.definition->'content') AS elem
        ),
        false
    )
WHERE ad.type = 'CUSTOM';

UPDATE attribute_content_item aci JOIN attribute_definition ad ON aci.attribute_definition_uuid = ad.uuid
SET json =
    jsonb_set(
        '{content}',
        (
            SELECT jsonb_agg(
                elem || jsonb_build_object('contentType', LOWER(ad.content_type))
            )
            FROM jsonb_array_elements(aci.json->'content') AS elem
        ),
        false
    )
WHERE ad.type = 'CUSTOM';

-- migrate custom attributes where they are only as a json column


