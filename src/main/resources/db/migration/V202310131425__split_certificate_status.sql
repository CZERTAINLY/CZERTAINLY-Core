ALTER TABLE certificate
    ADD COLUMN validation_status TEXT NULL,
    ADD COLUMN state TEXT NULL;

-- migrate certificate state
UPDATE certificate SET state = 'ISSUED';
UPDATE certificate SET state = 'REQUESTED' WHERE status = 'NEW';
UPDATE certificate SET state = 'REJECTED' WHERE status = 'REJECTED';
UPDATE certificate SET state = 'REVOKED' WHERE status = 'REVOKED';
UPDATE certificate SET state = 'PENDING_APPROVAL'
    WHERE status = 'NEW'
        AND (SELECT COUNT(*) FROM approval AS a WHERE a.object_uuid = certificate.uuid AND a.status = 'PENDING') > 0;
UPDATE certificate SET state = 'FAILED'
    WHERE status = 'NEW'
        AND (SELECT COUNT(*) FROM approval AS a WHERE a.object_uuid = certificate.uuid AND a.status = 'PENDING') = 0
		AND (SELECT COUNT(*) FROM approval AS a WHERE a.object_uuid = certificate.uuid AND a."action" <> 'ISSUE') = 0
		AND (SELECT COUNT(*) FROM approval AS a WHERE a.object_uuid = certificate.uuid AND a.status = 'APPROVED') > 0;

-- migrate certificate validation status
UPDATE certificate SET validation_status = status;
UPDATE certificate SET validation_status = 'NOT_CHECKED' WHERE status = 'NEW' OR status = 'REJECTED';

-- drop original column
ALTER TABLE certificate
    ALTER COLUMN validation_status SET NOT NULL,
    ALTER COLUMN state SET NOT NULL,
    DROP COLUMN status;

-- migrate Certificate event
UPDATE certificate_event_history SET event = 'REQUEST' WHERE event = 'CREATE_CSR';
UPDATE certificate_event_history SET event = 'UPDATE_VALIDATION_STATUS' WHERE event = 'UPDATE_STATUS';
