package com.otilm.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.oid.OidCategory;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.oid.OidRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionSchemasTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // The OidHandler cache is process-wide static state shared across the whole test JVM.
    // Snapshot CERTIFICATE_EXTENSION before this class replaces it; restore it afterwards.
    private static Map<String, OidRecord> savedExtensionCache;

    @BeforeAll
    static void snapshotExtensionCache() {
        Map<String, OidRecord> existing = OidHandler.getOidCache(OidCategory.CERTIFICATE_EXTENSION);
        savedExtensionCache = existing == null ? null : new HashMap<>(existing);
    }

    @AfterAll
    static void restoreExtensionCache() {
        OidHandler
                .cacheOidCategory(OidCategory.CERTIFICATE_EXTENSION,
                        savedExtensionCache != null ? savedExtensionCache : new HashMap<>());
    }

    @BeforeEach
    void clearExtensionRegistry() {
        OidHandler.cacheOidCategory(OidCategory.CERTIFICATE_EXTENSION, new HashMap<>());
    }

    @Test
    void shipsTheBasicConstraintsSchema() throws Exception {
        List<String> violations = ExtensionSchemas
                .validateShape("2.5.29.19", MAPPER.readTree("{\"sequence\":[{\"boolean\":true},{\"integer\":0}]}"));
        assertThat(violations).isEmpty();
    }

    @Test
    void basicConstraintsSchemaAcceptsTheEmptySequence() throws Exception {
        // 30 00 - cA absent, defaulting to FALSE. This is the canonical end-entity BasicConstraints and the
        // most common real value, so rejecting it would be worse than having no schema.
        assertThat(ExtensionSchemas.validateShape("2.5.29.19", MAPPER.readTree("{\"sequence\":[]}"))).isEmpty();
    }

    @Test
    void basicConstraintsSchemaRejectsAnExplicitFalseCa() throws Exception {
        // X.690 11.5: a DEFAULT value must not be encoded, so {boolean:false} would emit DER-invalid content.
        assertThat(ExtensionSchemas.validateShape("2.5.29.19", MAPPER.readTree("{\"sequence\":[{\"boolean\":false}]}")))
                .isNotEmpty();
    }

    @Test
    void basicConstraintsSchemaRejectsANegativePathLength() throws Exception {
        assertThat(ExtensionSchemas
                .validateShape("2.5.29.19", MAPPER.readTree("{\"sequence\":[{\"boolean\":true},{\"integer\":-5}]}")))
                .isNotEmpty();
    }

    @Test
    void tlsFeatureSchemaRejectsAValueOutsideTheIanaRange() throws Exception {
        assertThat(ExtensionSchemas
                .validateShape("1.3.6.1.5.5.7.1.24", MAPPER.readTree("{\"sequence\":[{\"integer\":-1}]}")))
                .isNotEmpty();
        assertThat(ExtensionSchemas
                .validateShape("1.3.6.1.5.5.7.1.24", MAPPER.readTree("{\"sequence\":[{\"integer\":65536}]}")))
                .isNotEmpty();
    }

    @Test
    void basicConstraintsSchemaRejectsAThirdElement() throws Exception {
        List<String> violations = ExtensionSchemas
                .validateShape("2.5.29.19",
                        MAPPER.readTree("{\"sequence\":[{\"boolean\":true},{\"integer\":0},{\"integer\":1}]}"));
        assertThat(violations).anySatisfy(v -> assertThat(v).contains("registered schema for extension 2.5.29.19"));
    }

    @Test
    void shipsTheTlsFeatureSchema() throws Exception {
        assertThat(ExtensionSchemas
                .validateShape("1.3.6.1.5.5.7.1.24", MAPPER.readTree("{\"sequence\":[{\"integer\":5}]}"))).isEmpty();
        assertThat(ExtensionSchemas
                .validateShape("1.3.6.1.5.5.7.1.24", MAPPER.readTree("{\"sequence\":[{\"oid\":\"1.2\"}]}")))
                .isNotEmpty();
    }

    @Test
    void aRegistryEntrySchemaBeatsTheShippedResource() throws Exception {
        OidHandler
                .cacheOid(OidCategory.CERTIFICATE_EXTENSION, "2.5.29.19",
                        OidRecord
                                .builder()
                                .displayName("Overridden")
                                .valueSchema("{\"type\":\"object\",\"required\":[\"set\"]}")
                                .build());

        assertThat(ExtensionSchemas.validateShape("2.5.29.19", MAPPER.readTree("{\"sequence\":[{\"boolean\":true}]}")))
                .isNotEmpty();
        assertThat(ExtensionSchemas.validateShape("2.5.29.19", MAPPER.readTree("{\"set\":[]}"))).isEmpty();
    }

    @Test
    void anUnknownOidHasNoSchema() throws Exception {
        assertThat(ExtensionSchemas.resolve("1.3.6.1.4.1.99999.77")).isEmpty();
        assertThat(ExtensionSchemas.validateShape("1.3.6.1.4.1.99999.77", MAPPER.readTree("{\"sequence\":[]}")))
                .isEmpty();
    }

    @Test
    void requireValidSchemaAcceptsASchemaAndRejectsGarbage() {
        ExtensionSchemas.requireValidSchema("{\"type\":\"object\"}");
        assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema("this is not json"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("valid JSON Schema document");
    }

    @Test
    void requireValidSchemaRejectsARootThatIsNotAnObjectOrBoolean() {
        // Each of these parses as JSON and compiles into a schema that constrains nothing, so an operator
        // would believe they had registered a shape and get none.
        for (String document : List.of("\"hello\"", "123", "null", "[]", "\"\"")) {
            assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema(document))
                    .as("document: %s", document)
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("object or a boolean");
        }
    }

    @Test
    void requireValidSchemaAcceptsABooleanRoot() {
        ExtensionSchemas.requireValidSchema("true");
        ExtensionSchemas.requireValidSchema("false");
    }

    @Test
    void requireValidSchemaRejectsARefPointingOutsideTheDocument() {
        for (String ref : List
                .of("https://evil.example.com/x.json", "http://169.254.169.254/latest/meta-data/",
                        "file:///etc/passwd")) {
            assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema("{\"$ref\":\"" + ref + "\"}"))
                    .as("ref: %s", ref)
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("points outside the document");
        }
    }

    @Test
    void requireValidSchemaRejectsANestedRemoteRef() {
        assertThatThrownBy(() -> ExtensionSchemas
                .requireValidSchema("{\"properties\":{\"a\":{\"items\":{\"$ref\":\"https://x/y\"}}}}"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("points outside the document");
    }

    @Test
    void requireValidSchemaAcceptsALocalFragmentRef() {
        ExtensionSchemas.requireValidSchema("{\"$defs\":{\"x\":{\"type\":\"integer\"}},\"$ref\":\"#/$defs/x\"}");
    }

    @Test
    void requireValidSchemaRejectsADeclaredOlderDialect() {
        // The factory default applies only when $schema is absent. Under draft-04, prefixItems is an unknown
        // keyword, so a schema that reads as restrictive would enforce nothing.
        String draft4 = "{\"$schema\":\"http://json-schema.org/draft-04/schema#\",\"type\":\"object\"}";

        assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema(draft4))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("draft 2020-12");
    }

    @Test
    void requireValidSchemaAcceptsTheDeclaredSupportedDialect() {
        String declared = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\"}";

        assertThatNoException().isThrownBy(() -> ExtensionSchemas.requireValidSchema(declared));
    }

    @Test
    void requireValidSchemaRejectsTrailingContent() {
        // readTree stops at the first complete value, so trailing text would be discarded unnoticed.
        assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema("{\"type\":\"object\"} and then some"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void requireValidSchemaRejectsMalformedKeywords() {
        // getSchema compiles these without complaint, so they would register and then constrain nothing.
        assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema("{\"minItems\":\"x\"}"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema("{\"type\":123}"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void requireValidSchemaRejectsAMissingDocument() {
        // readTree(null) throws IllegalArgumentException, which is not this method's contract.
        assertThatThrownBy(() -> ExtensionSchemas.requireValidSchema(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void requireValidSchemaAcceptsARefInsideInstanceData() {
        // const holds a literal value, so a member named $ref there is data and not a reference.
        assertThatNoException()
                .isThrownBy(() -> ExtensionSchemas
                        .requireValidSchema("{\"const\":{\"$ref\":\"https://example.invalid/x\"}}"));
    }

    @Test
    void anUnloadableStoredSchemaIsReportedRatherThanThrown() {
        // A value written straight into the database must not turn every request into a 500.
        OidHandler
                .cacheOid(OidCategory.CERTIFICATE_EXTENSION, "1.3.6.1.4.1.99999.9.9",
                        OidRecord.builder().displayName("Broken").valueSchema("not json at all").build());

        assertThat(ExtensionSchemas.validateShape("1.3.6.1.4.1.99999.9.9", MAPPER.createObjectNode()))
                .anySatisfy(message -> assertThat(message).contains("not loadable"));
    }

    @Test
    void aStoredSchemaWithARemoteRefIsNotFetched() {
        // requireValidSchema refuses a remote $ref, but a schema reaching the table by another route must not
        // make the server fetch a URL of the schema author's choosing. A blocked host would hang if it were
        // attempted; the assertion is that resolution fails locally and fast.
        OidHandler
                .cacheOid(OidCategory.CERTIFICATE_EXTENSION, "1.3.6.1.4.1.99999.9.10",
                        OidRecord
                                .builder()
                                .displayName("Exfiltrating")
                                .valueSchema("{\"$ref\":\"http://169.254.169.254/latest/meta-data/\"}")
                                .build());

        long startedAt = System.nanoTime();
        List<String> messages = ExtensionSchemas.validateShape("1.3.6.1.4.1.99999.9.10", MAPPER.createObjectNode());

        assertThat(messages).anySatisfy(message -> assertThat(message).contains("not loadable"));
        assertThat(System.nanoTime() - startedAt).isLessThan(java.time.Duration.ofSeconds(2).toNanos());
    }
}
