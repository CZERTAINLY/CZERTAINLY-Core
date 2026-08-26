package com.otilm.core.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.ClasspathSchemaLoader;
import com.networknt.schema.resource.DisallowSchemaLoader;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.oid.OidCategory;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.oid.OidRecord;
import com.otilm.core.serialization.ObjectMapperFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and applies the JSON Schema describing a DER-encoded extension's JSON value. Resolution order: the OID
 * registry entry's {@code valueSchema}, then a Core-shipped classpath resource ({@code extension-schemas/<oid>.json}),
 * else none — a schema-less extension accepts any tree the codec can encode.
 */
public final class ExtensionSchemas {

    // ObjectMapperFactory is the single home of production mapper recipes; reading a JSON tree needs
    // nothing beyond the wire recipe.
    private static final ObjectMapper MAPPER = ObjectMapperFactory.wire();
    // Schema loading must never reach the network: getSchema resolves $ref targets eagerly, so a schema
    // reaching the database by any route other than requireValidSchema would otherwise fetch a URL of its
    // author's choosing on the server's behalf.
    private static final Logger logger = LoggerFactory.getLogger(ExtensionSchemas.class);

    /**
     * Validates a candidate schema document against the dialect's own metaschema. Classpath loading is permitted so the
     * library's bundled metaschema resolves; the network stays refused.
     */
    private static final JsonSchema METASCHEMA = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012,
                    builder -> builder
                            .schemaLoaders(loaders -> loaders
                                    .add(new ClasspathSchemaLoader())
                                    .add(DisallowSchemaLoader.getInstance())))
            .getSchema(SchemaLocation.of(SpecVersion.VersionFlag.V202012.getId()));

    /** Keywords whose values are instance data rather than subschemas, so a {@code $ref} inside is a literal. */
    private static final Set<String> LITERAL_KEYWORDS = Set.of("const", "enum", "default", "examples");

    private static final Set<String> SUPPORTED_DIALECTS = Set
            .of("https://json-schema.org/draft/2020-12/schema", "https://json-schema.org/draft/2020-12/schema#");
    private static final Map<String, Optional<String>> SHIPPED = new ConcurrentHashMap<>();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012,
                    builder -> builder.schemaLoaders(loaders -> loaders.add(DisallowSchemaLoader.getInstance())));

    private ExtensionSchemas() {
    }

    /** The schema governing {@code oid}'s value, or empty when neither the registry nor Core ships one. */
    public static Optional<JsonSchema> resolve(String oid) {
        Map<String, OidRecord> registry = OidHandler.getOidCache(OidCategory.CERTIFICATE_EXTENSION);
        OidRecord record = registry == null ? null : registry.get(oid);
        if (record != null && record.valueSchema() != null) {
            return Optional.of(load(record.valueSchema()));
        }
        if (record != null && !record.system()) {
            // An operator's own entry is the effective one while it exists, the same way its criticality and
            // encoding win. Declaring no schema therefore means the value is unconstrained, not that a
            // Core-shipped shape applies — which would otherwise start constraining a legacy row whose OID has
            // since become a system OID.
            return Optional.empty();
        }
        return shippedSchema(oid).map(ExtensionSchemas::load);
    }

    /**
     * The Core-shipped schema document for {@code oid}, or empty when Core ships none. Exposed as text so the OID API
     * can show an operator the shape a system extension's value must take — a schema Core enforces but the registry row
     * cannot carry, because an entry for a system OID cannot be created.
     */
    public static Optional<String> shippedSchema(String oid) {
        // Classpath resources cannot change while the process runs, so the miss is worth caching too.
        return SHIPPED.computeIfAbsent(oid, ExtensionSchemas::readShippedSchema);
    }

    private static Optional<String> readShippedSchema(String oid) {
        try (InputStream resource = ExtensionSchemas.class
                .getClassLoader()
                .getResourceAsStream("extension-schemas/" + oid + ".json")) {
            if (resource == null) {
                return Optional.empty();
            }
            return Optional.of(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Core-shipped extension schema for " + oid + " is unreadable", e);
        }
    }

    /**
     * Rejects a schema document networknt cannot load. Applied to a registration's {@code valueSchema} and to a JSON
     * Schema constraint's data, so a broken document fails at save rather than at first use.
     */
    public static void requireValidSchema(String schemaDocument) {
        if (schemaDocument == null) {
            // readTree(null) throws IllegalArgumentException, which is not this method's contract.
            throw new ValidationException("Not a valid JSON Schema document: no document was supplied");
        }
        JsonNode parsed;
        try {
            parsed = MAPPER.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(schemaDocument);
        } catch (IOException e) {
            throw new ValidationException("Not a valid JSON Schema document: not JSON");
        }
        if (parsed == null || !(parsed.isObject() || parsed.isBoolean())) {
            throw new ValidationException("Not a valid JSON Schema document: the root must be an object or a boolean");
        }
        requireSupportedDialect(parsed);
        rejectNonLocalRefs(parsed, "$");
        requireWellFormedKeywords(parsed);
        try {
            FACTORY.getSchema(parsed);
        } catch (RuntimeException e) {
            // The library's own message names parser and resource-loading internals; this one reaches the
            // custom-OID API, so keep the detail in the log and hand the operator a controlled message.
            logger.debug("JSON Schema document could not be compiled", e);
            throw new ValidationException("Not a valid JSON Schema document: it could not be compiled");
        }
    }

    /**
     * Rejects a document whose keywords are malformed. {@code getSchema} compiles a schema without checking keyword
     * shapes, so {@code {"minItems": "x"}} or {@code {"type": 123}} would otherwise register and then constrain
     * nothing. Validating against the dialect's own metaschema is the check the document claims to satisfy.
     */
    private static void requireWellFormedKeywords(JsonNode document) {
        Set<String> messages = new java.util.LinkedHashSet<>();
        for (ValidationMessage violation : METASCHEMA.validate(document)) {
            messages.add("%s %s".formatted(violation.getInstanceLocation(), violation.getMessage()));
        }
        if (!messages.isEmpty()) {
            throw new ValidationException("Not a valid JSON Schema document: " + String.join("; ", messages));
        }
    }

    /**
     * Rejects a declared {@code $schema} other than draft 2020-12. The factory's default applies only when the document
     * omits the keyword, so a declared older draft would be honoured instead — and under draft-04 a keyword such as
     * {@code prefixItems} is unknown, so a schema that looks restrictive would enforce nothing.
     */
    private static void requireSupportedDialect(JsonNode document) {
        if (!document.isObject() || !document.has("$schema")) {
            return;
        }
        JsonNode declared = document.get("$schema");
        if (!declared.isTextual() || !SUPPORTED_DIALECTS.contains(declared.textValue())) {
            throw new ValidationException(
                    "Not a valid JSON Schema document: only the draft 2020-12 dialect is supported");
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
                if (LITERAL_KEYWORDS.contains(property.getKey())) {
                    // const, enum and friends hold instance data, not subschemas, so a member named $ref inside
                    // one is a plain value. The disabled loader remains the defence if one is ever a reference.
                    continue;
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
        try {
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
        } catch (RuntimeException e) {
            // A schema written straight into the database, or saved before a tightening, must not turn every
            // request for this extension into a 500. A $ref is resolved lazily, so an unloadable one surfaces
            // during validation rather than from resolve.
            return List.of("cannot be checked: the registered schema for extension %s is not loadable".formatted(oid));
        }
    }

    private static JsonSchema load(String schemaDocument) {
        try {
            return FACTORY.getSchema(MAPPER.readTree(schemaDocument));
        } catch (IOException e) {
            throw new IllegalStateException("not JSON: " + e.getMessage(), e);
        }
    }
}
