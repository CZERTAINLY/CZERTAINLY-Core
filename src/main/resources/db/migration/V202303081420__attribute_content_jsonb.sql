CREATE TABLE attribute_content_item
(
    uuid                   uuid not null,
    attribute_content_uuid uuid not null,
    json                   JSONB,
    PRIMARY KEY (uuid)
);
