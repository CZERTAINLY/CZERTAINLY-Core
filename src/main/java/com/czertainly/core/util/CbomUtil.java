package com.czertainly.core.util;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import com.czertainly.api.exception.ValidationException;

public final class CbomUtil {
    private CbomUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static Map<String, Object> getMetadata(Map<String, Object> content) throws ValidationException {
        Object metadataObj = content.get("metadata");
        if (metadataObj == null) {
            throw new ValidationException("metadata must be present");
        }
        if (!(metadataObj instanceof Map)) {
            throw new ValidationException("metadata must be JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) metadataObj;
        return metadata;
    }

    // get the metadata.component.name field if exists
    public static Optional<String> getMetadataComponentName(Map<String, Object> content) {
        try {
            Map<String, Object> metadata = getMetadata(content);
            return Optional.ofNullable(metadata.get("component"))
             .filter(Map.class::isInstance)
             .map(Map.class::cast)
             .map(m -> m.get("name"))
             .filter(o -> o != null)
            .map(String::valueOf);
        } catch (ValidationException e) {
            return Optional.empty();
        }
    }

    // get the metadata.timestamp field if exists
    public static Optional<OffsetDateTime> getMetadataTimestamp(Map<String, Object> content) {
        try {
            Map<String, Object> metadata = getMetadata(content);
            String timestampStr = (String) metadata.get("timestamp");
            OffsetDateTime timestamp = OffsetDateTime.parse(timestampStr);
            return Optional.ofNullable(timestamp);
        } catch (Exception e) {
            return Optional.empty();
        }
     }

    public static Optional<String> getString(Map<String, Object> content, String key) {
        return Optional.ofNullable(content.get(key))
        .map(Object::toString);
    }
}
