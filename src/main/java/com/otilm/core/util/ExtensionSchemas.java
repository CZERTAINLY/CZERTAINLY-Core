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
        JsonNode parsed;
        try {
            parsed = MAPPER.readTree(schemaDocument);
        } catch (IOException e) {
            throw new ValidationException("Not a valid JSON Schema document: not JSON");
        }
        if (parsed == null || !(parsed.isObject() || parsed.isBoolean())) {
            throw new ValidationException("Not a valid JSON Schema document: the root must be an object or a boolean");
        }
        rejectNonLocalRefs(parsed, "$");
        try {
            FACTORY.getSchema(parsed);
        } catch (RuntimeException e) {
            throw new ValidationException("Not a valid JSON Schema document: " + e.getMessage());
        }
    }

    /**
     * Rejects a {@code $ref} that points outside the document. Such a reference cannot be resolved without fetching it,
     * which the platform does not do, so accepting one would register a schema that silently constrains nothing.
     */
    private static void rejectNonLocalRefs(JsonNode node, String path) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                if ("$ref".equals(property.getKey()) && property.getValue().isTextual()
                        && !property.getValue().textValue().startsWith("#")) {
                    throw new ValidationException(
                            "Not a valid JSON Schema document: $ref at %s points outside the document; only local references such as #/$defs/name are supported"
                                    .formatted(path));
                }
                rejectNonLocalRefs(property.getValue(), path + "." + property.getKey());
            }
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                rejectNonLocalRefs(child, "%s[%d]".formatted(path, index++));
            }
        }
    }

    /**
     * Validates {@code value} against {@code oid}'s resolved schema. Messages carry the registry-layer wording,
     * distinct from the constraint layer's, so an operator sees which schema rejected.
     */
    public static List<String> validateShape(String oid, JsonNode value) {
        Optional<JsonSchema> schema;
        try {
            schema = resolve(oid);
        } catch (RuntimeException e) {
            // A schema written straight into the database, or saved before a tightening, must not turn every
            // request for this extension into a 500.
            return List.of("cannot be checked: the registered schema for extension %s is not loadable".formatted(oid));
        }
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
