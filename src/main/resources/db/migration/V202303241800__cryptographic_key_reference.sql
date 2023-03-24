DELETE FROM attribute_content_2_object a
WHERE a.object_type = 'CRYPTOGRAPHIC_KEY'
AND  a.source_object_type = 'CRYPTOGRAPHIC_KEY'
AND a.object_uuid NOT IN (SELECT key_reference_uuid FROM cryptographic_key_item);

with ct2a AS (SELECT a.object_uuid, c.key_reference_uuid, c.uuid
 FROM attribute_content_2_object a
 JOIN cryptographic_key_item c
 ON a.object_uuid = c.key_reference_uuid
 WHERE a.object_type = 'CRYPTOGRAPHIC_KEY'
 AND  a.source_object_type = 'CRYPTOGRAPHIC_KEY')
 UPDATE attribute_content_2_object AS a1
 SET object_uuid = ct2a.uuid FROM ct2a
 WHERE a1.object_uuid = ct2a.key_reference_uuid;