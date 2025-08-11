WITH mapped AS (
  SELECT
    uuid,
    jsonb_agg(
      CASE code
        WHEN 'digitalSignature' THEN 'Digital Signature'
        WHEN 'nonRepudiation' THEN 'Non Repudiation'
        WHEN 'keyEncipherment' THEN 'Key Encipherment'
        WHEN 'dataEncipherment' THEN 'Data Encipherment'
        WHEN 'keyAgreement' THEN 'Key Agreement'
        WHEN 'keyCertSign' THEN 'Key Cert Sign'
        WHEN 'cRLSign' THEN 'CRL Sign'
        WHEN 'encipherOnly' THEN 'Encipher Only'
        WHEN 'decipherOnly' THEN 'Decipher Only'
      END
    ) AS labels
  FROM certificate,
       jsonb_array_elements_text(key_usage::jsonb) AS code
  GROUP BY uuid
)
UPDATE certificate c
SET key_usage = m.labels
FROM mapped m
WHERE c.uuid = m.uuid;
