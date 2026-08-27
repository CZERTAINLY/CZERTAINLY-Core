package com.otilm.core.attribute.engine;

import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.ValidationError;
import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.client.attribute.RequestAttributeV3;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.properties.DataAttributeProperties;
import com.otilm.api.model.common.attribute.v3.DataAttributeV3;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.mapping.ExtensionMappedField;
import com.otilm.api.model.common.attribute.v3.mapping.FieldMapping;
import com.otilm.api.model.common.attribute.v3.mapping.FieldType;
import com.otilm.api.model.common.attribute.v3.mapping.ObjectType;
import com.otilm.api.model.core.oid.ExtensionValueEncoding;
import com.otilm.api.model.core.oid.OidCategory;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.oid.OidRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeEngineJsonExtensionValueTest {

    private static final String CUSTOM_OID = "1.3.6.1.4.1.99999.1.1";

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
    void seedRegistry() {
        OidHandler.cacheOidCategory(OidCategory.CERTIFICATE_EXTENSION, new HashMap<>());
        register(CUSTOM_OID, ExtensionValueEncoding.DER,
                "{\"type\":\"object\",\"properties\":{\"sequence\":{\"type\":\"array\",\"minItems\":2}},"
                        + "\"required\":[\"sequence\"]}");
    }

    @Test
    void acceptsAValueMatchingTheRegistrySchema() {
        var definition = extensionDefinition(CUSTOM_OID);

        List<ValidationError> errors = AttributeEngine
                .validateJsonExtensionValues(definition,
                        value(definition, "{\"sequence\":[{\"integer\":2},{\"utf8String\":\"gateway\"}]}"));

        assertThat(errors).isEmpty();
    }

    @Test
    void rejectsAValueViolatingTheRegistrySchema_withTheRegistryWording() {
        var definition = extensionDefinition(CUSTOM_OID);

        List<ValidationError> errors = AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "{\"sequence\":[{\"integer\":2}]}"));

        assertThat(errors)
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorDescription())
                        .contains("registered schema for extension " + CUSTOM_OID)
                        .contains("$.sequence"));
    }

    @Test
    void rejectsAGrammaticallyInvalidTree_beforeTheSchemaLayer() {
        var definition = extensionDefinition(CUSTOM_OID);

        List<ValidationError> errors = AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "{\"sequence\":[{\"int\":2}]}"));

        assertThat(errors)
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorDescription())
                        .contains("Unknown node type 'int'")
                        .contains("$.sequence[0]")
                        .doesNotContain("registered schema"));
    }

    @Test
    void leavesALegacyBase64ValueAlone() {
        // The legacy path was never shape-checked; only values starting with '{' are.
        var definition = extensionDefinition(CUSTOM_OID);

        List<ValidationError> errors = AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "MAYBAf8CAQA="));

        assertThat(errors).isEmpty();
    }

    @Test
    void acceptsAnyValidTree_whenTheOidDeclaresNoSchema() {
        register("1.3.6.1.4.1.99999.2.2", ExtensionValueEncoding.DER, null);
        var definition = extensionDefinition("1.3.6.1.4.1.99999.2.2");

        List<ValidationError> errors = AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "{\"set\":[{\"oid\":\"1.2.3\"}]}"));

        assertThat(errors).isEmpty();
    }

    @Test
    void ignoresAnExtensionWhoseEncodingIsNotDer() {
        register("1.3.6.1.4.1.99999.3.3", ExtensionValueEncoding.UTF8_STRING, null);
        var definition = extensionDefinition("1.3.6.1.4.1.99999.3.3");

        List<ValidationError> errors = AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "{not even json"));

        assertThat(errors).isEmpty();
    }

    @Test
    void appliesTheShippedBasicConstraintsSchemaForABuiltInEntry() {
        registerSystem("2.5.29.19");
        var definition = extensionDefinition("2.5.29.19");

        assertThat(AttributeEngine
                .validateJsonExtensionValues(definition,
                        value(definition, "{\"sequence\":[{\"boolean\":true},{\"integer\":0}]}")))
                .isEmpty();
        assertThat(AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "{\"sequence\":[{\"integer\":0}]}")))
                .isNotEmpty();
    }

    @Test
    void appliesTheShippedSchemaWhenTheRegistryHasNoEntry() {
        var definition = extensionDefinition("2.5.29.19");

        assertThat(AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "{\"sequence\":[{\"integer\":0}]}")))
                .isNotEmpty();
    }

    @Test
    void aCustomEntryDeclaringNoSchemaLeavesTheValueUnconstrained() {
        // An operator's own entry is the effective one while it exists, so declaring no schema means
        // unconstrained — not that the Core-shipped shape for the same OID starts applying.
        register("2.5.29.19", ExtensionValueEncoding.DER, null);
        var definition = extensionDefinition("2.5.29.19");

        assertThat(AttributeEngine
                .validateJsonExtensionValues(definition, value(definition, "{\"sequence\":[{\"integer\":0}]}")))
                .isEmpty();
    }

    @Test
    void refusesAJsonTreeForAnExtensionThatHasATypedTarget() {
        // Authoring a new opaque mapping for these OIDs is already refused; a legacy one must not gain a
        // second, weaker way in. The typed target draws on a closed vocabulary and cannot express a malformed
        // value, which is exactly what a hand-written bit string can do.
        registerSystem("2.5.29.15");
        var definition = extensionDefinition("2.5.29.15");

        assertThat(AttributeEngine
                .validateJsonExtensionValues(definition,
                        value(definition, "{\"bitString\":{\"value\":\"gA==\",\"padBits\":7}}")))
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorDescription()).contains("Key Usage"));

        // Base64 DER through a legacy mapping is untouched.
        assertThat(AttributeEngine.validateJsonExtensionValues(definition, value(definition, "AwIFoA=="))).isEmpty();
    }

    private static void registerSystem(String oid) {
        OidHandler
                .cacheOid(OidCategory.CERTIFICATE_EXTENSION, oid,
                        OidRecord
                                .builder()
                                .displayName("Built-in Extension")
                                .valueEncoding(ExtensionValueEncoding.DER)
                                .system(true)
                                .build());
    }

    private static void register(String oid, ExtensionValueEncoding encoding, String schema) {
        OidHandler
                .cacheOid(OidCategory.CERTIFICATE_EXTENSION, oid,
                        OidRecord
                                .builder()
                                .displayName("Test Extension")
                                .valueEncoding(encoding)
                                .valueSchema(schema)
                                .build());
    }

    @Test
    void everyDeclaredContentErrorIsReportedNotJustTheFirst() throws Exception {
        // Declared content with two bad values must name both; surfacing only the first sends the author round
        // the loop once per mistake.
        DataAttributeV3 definition = extensionDefinition("2.5.29.19");
        definition
                .setContent(List
                        .of(new StringAttributeContentV3("{\"sequence\":[{\"boolean\":false}]}"),
                                new StringAttributeContentV3("{\"sequence\":[{\"boolean\":true},{\"integer\":-1}]}")));

        AttributeException thrown = Assertions
                .assertThrows(AttributeException.class,
                        () -> AttributeEngine.validateJsonSchemaDeclarations(definition, null));

        // Two distinct violations, joined rather than truncated to the first.
        assertThat(thrown.getMessage()).contains("; ");
        assertThat(thrown.getMessage().split("; ")).hasSizeGreaterThan(1);
    }

    private static DataAttributeV3 extensionDefinition(String oid) {
        ExtensionMappedField field = new ExtensionMappedField();
        field.setFieldType(FieldType.EXTENSION);
        field.setExtensionOid(oid);
        FieldMapping mapping = new FieldMapping();
        mapping.setObjectType(ObjectType.X509_CERTIFICATE);
        mapping.setFields(List.of(field));

        DataAttributeV3 definition = new DataAttributeV3();
        definition.setUuid(UUID.randomUUID().toString());
        definition.setName("serviceIdentity");
        definition.setContentType(AttributeContentType.STRING);
        DataAttributeProperties properties = new DataAttributeProperties();
        properties.setLabel("Service Identity");
        definition.setProperties(properties);
        definition.setFieldMapping(mapping);
        return definition;
    }

    private static RequestAttribute value(DataAttributeV3 definition, String data) {
        return new RequestAttributeV3(UUID.fromString(definition.getUuid()), definition.getName(),
                AttributeContentType.STRING, List.<BaseAttributeContentV3<?>>of(new StringAttributeContentV3(data)));
    }
}
