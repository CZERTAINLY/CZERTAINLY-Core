UPDATE attribute_definition
SET definition = jsonb_set(definition, '{content}', 'null'::jsonb)
WHERE (
    definition -> 'content' IS NOT NULL
        AND definition -> 'content' <> 'null'::jsonb
    )
  AND (definition -> 'properties' ->> 'readOnly')::boolean = false

  AND type = 'DATA'
  AND connector_uuid IS NOT NULL;
