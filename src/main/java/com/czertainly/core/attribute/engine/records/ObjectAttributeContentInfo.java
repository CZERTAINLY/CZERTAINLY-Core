package com.czertainly.core.attribute.engine.records;

import com.czertainly.api.model.core.auth.Resource;

import java.util.Objects;
import java.util.UUID;

public record ObjectAttributeContentInfo(
        UUID connectorUuid,
        Resource objectType,
        UUID objectUuid,
        Resource sourceObjectType,
        UUID sourceObjectUuid,
        String sourceObjectName
) {
    public ObjectAttributeContentInfo {
        Objects.requireNonNull(objectType);
        Objects.requireNonNull(objectUuid);
    }

    public ObjectAttributeContentInfo(Resource objectType, UUID objectUuid) {
        this(null, objectType, objectUuid, null, null, null);
    }

    public ObjectAttributeContentInfo(Resource objectType, UUID objectUuid, Resource sourceObjectType, UUID sourceObjectUuid) {
        this(null, objectType, objectUuid, sourceObjectType, sourceObjectUuid, null);
    }

    public ObjectAttributeContentInfo(UUID connectorUuid, Resource objectType, UUID objectUuid) {
        this(connectorUuid, objectType, objectUuid, null, null, null);
    }

    public ObjectAttributeContentInfo(UUID connectorUuid, Resource objectType, UUID objectUuid, Resource sourceObjectType, UUID sourceObjectUuid) {
        this(connectorUuid, objectType, objectUuid, sourceObjectType, sourceObjectUuid, null);
    }
}
