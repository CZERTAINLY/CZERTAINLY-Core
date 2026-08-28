package com.otilm.core.util;

import com.fasterxml.jackson.core.JsonParser;
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
    // Schema loading must never reach the network. A $ref target is resolved on first use rather than when
    // the schema is compiled, so a schema reaching the table by any route other than requireValidSchema would
    // otherwise fetch a URL of its author's choosing partway through validating a request.
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

    // Draft 2020-12 keywords whose values are subschemas. Walking only these keeps the check off instance data
    // (const, enum, default, examples) and off unknown keywords, which the dialect permits as annotations — a
    // member named $ref inside either is a literal. "definitions" is absent for that reason: 2020-12 replaced
    // it with $defs and treats it as an annotation. DisallowSchemaLoader remains the boundary for anything this
    // list does not reach.
    /** Both reference keywords the dialect defines; networknt resolves each lazily. */
    private static final Set<String> REFERENCE_KEYWORDS = Set.of("$ref", "$dynamicRef");

    private static final Set<String> SUBSCHEMA_KEYWORDS = Set
            .of("additionalProperties", "items", "not", "if", "then", "else", "contains", "propertyNames",
                    "unevaluatedItems", "unevaluatedProperties");
    private static final Set<String> SUBSCHEMA_LIST_KEYWORDS = Set.of("allOf", "anyOf", "oneOf", "prefixItems");
    private static final Set<String> SUBSCHEMA_MAP_KEYWORDS = Set
            .of("properties", "patternProperties", "$defs", "dependentSchemas");

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
        OidRecord oidRecord = registry == null ? null : registry.get(oid);
        if (oidRecord != null && oidRecord.valueSchema() != null) {
            return Optional.of(load(oidRecord.valueSchema()));
        }
        if (oidRecord != null && !oidRecord.system()) {
            // An operator's own entry is the effective one while it exists, the same way its criticality and
            // encoding win. Declaring no schema therefore means the value is unconstrained, not that a
            // Core-shipped shape applies — which would otherwise start constraining a legacy row whose OID has
            // since become a system OID.
            return Optional.empty();
        }
        return shippedSchema(oid).map(ExtensionSchemas::load);
    }

    /**
     * The Core-shipped schema document for {@code oid}, or empty when Core ships none.
     *
     * <p>
     * Text rather than a compiled schema, so the OID API can show it. A system OID's registry row cannot carry the
     * schema, because an entry for one cannot be created.
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
     * Rejects a schema document that cannot be trusted to constrain anything: it must parse as exactly one JSON value,
     * declare no dialect but draft 2020-12, reference nothing outside itself, carry well-formed keywords, and compile.
     *
     * <p>
     * A reference resolved only on use escapes all of that; {@link #validateShape} reports it as unverifiable.
     */
    public static void requireValidSchema(String schemaDocument) {
        if (schemaDocument == null) {
            // readTree(null) throws IllegalArgumentException, which is not this method's contract.
            throw new ValidationException("Not a valid JSON Schema document: no document was supplied");
        }
        JsonNode parsed;
        try {
            parsed = MAPPER
                    .reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readTree(schemaDocument);
        } catch (IOException e) {
            throw new ValidationException("Not a valid JSON Schema document: not JSON");
        }
        if (parsed == null || !(parsed.isObject() || parsed.isBoolean())) {
            throw new ValidationException("Not a valid JSON Schema document: the root must be an object or a boolean");
        }
        requireSupportedDialect(parsed);
        rejectNonLocalRefs(parsed, "$", documentId(parsed));
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
    private static void rejectNonLocalRefs(JsonNode node, String path, String documentId) {
        if (node == null || !node.isObject()) {
            return;
        }
        for (String keyword : REFERENCE_KEYWORDS) {
            JsonNode ref = node.get(keyword);
            if (ref != null && ref.isTextual() && !resolvesWithinDocument(ref.textValue(), documentId)) {
                throw new ValidationException(
                        "Not a valid JSON Schema document: %s at %s points outside the document; only local references such as #/$defs/name are supported"
                                .formatted(keyword, path));
            }
        }
        for (Map.Entry<String, JsonNode> property : node.properties()) {
            String childPath = path + "." + property.getKey();
            subschemasOf(property.getKey(), property.getValue())
                    .forEach(child -> rejectNonLocalRefs(child, childPath, documentId));
        }
    }

    /** The document's own {@code $id}, against which an absolute self-reference resolves, or {@code null}. */
    private static String documentId(JsonNode document) {
        JsonNode id = document == null ? null : document.get("$id");
        return id != null && id.isTextual() ? id.textValue() : null;
    }

    /**
     * Whether a reference stays inside the document: empty (the root), a fragment, or absolute but matching the
     * document's own {@code $id}.
     *
     * <p>
     * A nested {@code $id} re-scopes the base URI, which this does not follow — a reference relying on one falls
     * through to the loader, which refuses it, so the failure surfaces later rather than being missed.
     */
    private static boolean resolvesWithinDocument(String ref, String documentId) {
        if (ref.isEmpty() || ref.startsWith("#")) {
            return true;
        }
        if (documentId == null) {
            return false;
        }
        int fragment = ref.indexOf('#');
        return documentId.equals(fragment < 0 ? ref : ref.substring(0, fragment));
    }

    /** The subschemas a keyword's value holds, or nothing when the keyword does not hold subschemas. */
    private static List<JsonNode> subschemasOf(String keyword, JsonNode value) {
        if (SUBSCHEMA_KEYWORDS.contains(keyword)) {
            return List.of(value);
        }
        List<JsonNode> children = new ArrayList<>();
        if (SUBSCHEMA_LIST_KEYWORDS.contains(keyword) && value.isArray()) {
            value.forEach(children::add);
        } else if (SUBSCHEMA_MAP_KEYWORDS.contains(keyword) && value.isObject()) {
            value.properties().forEach(entry -> children.add(entry.getValue()));
        }
        return children;
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
            // during validation rather than from resolve. The operator's message cannot say which, so the
            // cause is logged for whoever has to tell malformed stored data from a defect.
            logger.warn("Registered schema for extension {} could not be applied", oid, e);
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
