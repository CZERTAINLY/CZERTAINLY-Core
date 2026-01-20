ALTER TABLE attribute_definition ADD COLUMN version INT NOT NULL DEFAULT 2;
UPDATE attribute_definition SET version = 3 WHERE type = 'CUSTOM';

UPDATE attribute_definition ad
SET definition =
        jsonb_set(
        jsonb_set(ad.definition, '{schemaVersion}', '"v3"', true),
        '{version}', '3', true)
WHERE ad.type = 'CUSTOM';


UPDATE attribute_definition ad
SET definition =
    jsonb_set(
        ad.definition,
        '{content}',
        (
            SELECT jsonb_agg(
                elem || jsonb_build_object('contentType', LOWER(ad.content_type))
            )
            FROM jsonb_array_elements(ad.definition->'content') AS elem
        ),
        false
    )
WHERE ad.type = 'CUSTOM' AND ad.definition ? 'content' AND jsonb_array_length(ad.definition->'content') > 0;

UPDATE attribute_content_item aci
SET json = jsonb_set(
        aci.json,
        '{contentType}',
        to_jsonb(LOWER(ad.content_type)),
        true
    )
FROM attribute_definition ad
WHERE aci.attribute_definition_uuid = ad.uuid
  AND ad.type = 'CUSTOM';


-- migrate custom attributes where they are only as a json column

UPDATE execution_item ei
SET data = (
    SELECT jsonb_agg(
               elem || jsonb_build_object(
                   'contentType', LOWER(SPLIT_PART(ei.field_identifier, '|', 2))
               )
           )
    FROM jsonb_array_elements(ei.data) AS elem
)
WHERE field_source = 'CUSTOM';

UPDATE protocol_certificate_associations
SET custom_attributes = (
  SELECT jsonb_agg(
           outer_elem
           || jsonb_build_object(
                'version', '3',
                'content',
                CASE
                  WHEN jsonb_typeof(outer_elem->'content') = 'array'
                  THEN COALESCE(
                         (
                           SELECT jsonb_agg(
                                    inner_elem || jsonb_build_object(
                                      'contentType', outer_elem->>'contentType'
                                    )
                                  )
                           FROM jsonb_array_elements(outer_elem->'content') AS inner_elem
                           WHERE inner_elem IS NOT NULL
                         ),
                         '[]'::jsonb
                       )
                  ELSE outer_elem->'content'
                END
              )
         )
  FROM jsonb_array_elements(custom_attributes) AS outer_elem
)
WHERE custom_attributes != '[]';


