ALTER TABLE acme_order
  DROP CONSTRAINT "acme_order_to_certificate_key",
  ADD CONSTRAINT "acme_order_to_certificate_key" FOREIGN KEY ("certificate_ref") REFERENCES certificate("uuid") ON DELETE SET NULL;

ALTER TABLE acme_account ADD COLUMN valid_orders INTEGER;
ALTER TABLE acme_account ADD COLUMN failed_orders INTEGER;

UPDATE acme_account ac
SET valid_orders = (
    SELECT COUNT(*)
    FROM acme_order ao
    WHERE ao.account_uuid = ac.uuid
      AND ao.status = 'VALID'
);

UPDATE acme_account ac
SET failed_orders = (
    SELECT COUNT(*)
    FROM acme_order ao
    WHERE ao.account_uuid = ac.uuid
      AND ao.status = 'INVALID'
);
