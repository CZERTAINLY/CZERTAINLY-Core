ALTER TABLE trigger_association
    ADD COLUMN resource_event TEXT
    ADD COLUMN override BOOLEAN NOT NULL;