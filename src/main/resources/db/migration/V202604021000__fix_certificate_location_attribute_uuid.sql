-- Fix push_attributes and csr_attributes in certificate_location table.
-- Old records may contain BaseAttribute JSON objects where the 'uuid' field is
-- missing, null, or empty (stored as request/response attributes without UUID).
-- Records may also be stored as a single JSON object instead of an array —
-- these are wrapped into an array to match the expected List<BaseAttribute> format.
-- UUID.fromString() in AttributeEngine.getResponseAttributesFromBaseAttributes()
-- throws an exception for such values, so we replace them with a default UUID.

WITH push_data AS (
    SELECT location_uuid,
           certificate_uuid,
           push_attributes::jsonb AS val
    FROM certificate_location
    WHERE push_attributes IS NOT NULL
      AND push_attributes NOT IN ('null', '[]')
      AND jsonb_typeof(push_attributes::jsonb) IN ('array', 'object')
)
UPDATE certificate_location cl
SET push_attributes = (
    SELECT jsonb_agg(
        CASE
            WHEN NOT (elem ? 'uuid')
              OR (elem->>'uuid') IS NULL
              OR trim(elem->>'uuid') = ''
            THEN jsonb_set(elem, '{uuid}', '"00000000-0000-0000-0000-000000000000"', true)
            ELSE elem
        END
    )::text
    FROM jsonb_array_elements(
        CASE WHEN jsonb_typeof(d.val) = 'array'
             THEN d.val
             ELSE jsonb_build_array(d.val)
        END
    ) AS elem
)
FROM push_data d
WHERE cl.location_uuid = d.location_uuid
  AND cl.certificate_uuid = d.certificate_uuid
  AND CASE
    WHEN jsonb_typeof(d.val) = 'object' THEN true
    WHEN jsonb_typeof(d.val) = 'array' THEN EXISTS (
        SELECT 1
        FROM jsonb_array_elements(d.val) AS elem
        WHERE NOT (elem ? 'uuid')
           OR (elem->>'uuid') IS NULL
           OR trim(elem->>'uuid') = ''
    )
    ELSE false
  END;

WITH csr_data AS (
    SELECT location_uuid,
           certificate_uuid,
           csr_attributes::jsonb AS val
    FROM certificate_location
    WHERE csr_attributes IS NOT NULL
      AND csr_attributes NOT IN ('null', '[]')
      AND jsonb_typeof(csr_attributes::jsonb) IN ('array', 'object')
)
UPDATE certificate_location cl
SET csr_attributes = (
    SELECT jsonb_agg(
        CASE
            WHEN NOT (elem ? 'uuid')
              OR (elem->>'uuid') IS NULL
              OR trim(elem->>'uuid') = ''
            THEN jsonb_set(elem, '{uuid}', '"00000000-0000-0000-0000-000000000000"', true)
            ELSE elem
        END
    )::text
    FROM jsonb_array_elements(
        CASE WHEN jsonb_typeof(d.val) = 'array'
             THEN d.val
             ELSE jsonb_build_array(d.val)
        END
    ) AS elem
)
FROM csr_data d
WHERE cl.location_uuid = d.location_uuid
  AND cl.certificate_uuid = d.certificate_uuid
  AND CASE
    WHEN jsonb_typeof(d.val) = 'object' THEN true
    WHEN jsonb_typeof(d.val) = 'array' THEN EXISTS (
        SELECT 1
        FROM jsonb_array_elements(d.val) AS elem
        WHERE NOT (elem ? 'uuid')
           OR (elem->>'uuid') IS NULL
           OR trim(elem->>'uuid') = ''
    )
    ELSE false
  END;
