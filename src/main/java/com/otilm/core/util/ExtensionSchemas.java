package com.otilm.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.oid.OidCategory;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.oid.OidRecord;
import com.otilm.core.serialization.ObjectMapperFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves and applies the JSON Schema describing a DER-encoded extension's JSON value. Resolution order: the OID
 * registry entry's {@code valueSchema}, then a Core-shipped classpath resource ({@code extension-schemas/<oid>.json}),
 * else none — a schema-less extension accepts any tree the codec can encode.
 */
public final class ExtensionSchemas {

    // ObjectMapperFactory is the single home of production mapper recipes; reading a JSON tree needs
    // nothing beyond the wire recipe.
    private static final ObjectMapper MAPPER = ObjectMapperFactory.wire();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private ExtensionSchemas() {
    }

    /** The schema governing {@code oid}'s value, or empty when neither the registry nor Core ships one. */
    public static Optional<JsonSchema> resolve(String oid) {
        Map<String, OidRecord> registry = OidHandler.getOidCache(OidCategory.CERTIFICATE_EXTENSION);
        OidRecord record = registry == null ? null : registry.get(oid);
        if (record != null && record.valueSchema() != null) {
            return Optional.of(load(record.valueSchema()));
        }
        try (InputStream resource = ExtensionSchemas.class
                .getClassLoader()
                .getResourceAsStream("extension-schemas/" + oid + ".json")) {
            if (resource == null) {
                return Optional.empty();
            }
            return Optional.of(FACTORY.getSchema(MAPPER.readTree(resource)));
        } catch (IOException e) {
            throw new IllegalStateException("Core-shipped extension schema for " + oid + " is unreadable", e);
        }
    }

    /**
     * Rejects a schema document networknt cannot load. Applied to a registration's {@code valueSchema} and to a JSON
     * Schema constraint's data, so a broken document fails at save rather than at first use.
     */
    public static void requireValidSchema(String schemaDocument) {
        try {
            load(schemaDocument);
        } catch (RuntimeException e) {
            throw new ValidationException("Not a valid JSON Schema document: " + e.getMessage());
        }
    }

    /**
     * Validates {@code value} against {@code oid}'s resolved schema. Messages carry the registry-layer wording,
     * distinct from the constraint layer's, so an operator sees which schema rejected.
     */
    public static List<String> validateShape(String oid, JsonNode value) {
        Optional<JsonSchema> schema = resolve(oid);
        if (schema.isEmpty()) {
            return List.of();
        }
        List<String> messages = new ArrayList<>();
        for (ValidationMessage violation : schema.get().validate(value)) {
            messages
                    .add("does not match the registered schema for extension %s (at %s): %s"
                            .formatted(oid, violation.getInstanceLocation(), violation.getMessage()));
        }
        return messages;
    }

    private static JsonSchema load(String schemaDocument) {
        try {
            return FACTORY.getSchema(MAPPER.readTree(schemaDocument));
        } catch (IOException e) {
            throw new IllegalStateException("not JSON: " + e.getMessage(), e);
        }
    }
}
