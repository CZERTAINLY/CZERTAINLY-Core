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
        // given — a custom entry for 2.5.29.19 demanding an entirely different shape
        OidHandler
                .cacheOid(OidCategory.CERTIFICATE_EXTENSION, "2.5.29.19",
                        OidRecord
                                .builder()
                                .displayName("Overridden")
                                .valueSchema("{\"type\":\"object\",\"required\":[\"set\"]}")
                                .build());

        // then — the shipped resource's happy value now fails, the entry's shape passes
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
}
