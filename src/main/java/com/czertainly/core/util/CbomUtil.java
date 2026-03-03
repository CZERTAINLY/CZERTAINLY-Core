package com.czertainly.core.util;

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

    public static String getMetadataSource(Map<String, Object> content) throws ValidationException {
        Map<String, Object> metadata = getMetadata(content);
        return Optional.ofNullable(metadata.get("component"))
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(m -> m.get("name"))
            .map(String::valueOf)
            .orElse("");
    }

    public static String getString(Map<String, Object> content, String key, String dflt) {
        return Optional.ofNullable(content.get(key))
                .map(Object::toString)
                .orElse(dflt);
    }
}
