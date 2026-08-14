package com.otilm.core.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the recipes whose behaviour is not obvious from the factory method alone.
 */
class ObjectMapperFactoryTest {

    /**
     * The exported columns are open-typed, so a producer anywhere may hand the export a JDK8 value.
     */
    @Test
    void auditLogExportSerializesJdk8TypesThroughTheirDiscoveredModule() throws JsonProcessingException {
        Map<String, Object> additionalData = new LinkedHashMap<>();
        additionalData.put("present", Optional.of("value"));
        additionalData.put("count", OptionalInt.of(3));

        String json = ObjectMapperFactory.auditLogExport().writeValueAsString(additionalData);

        assertThat(json).isEqualTo("{\"present\":\"value\",\"count\":3}");
    }

    @Test
    void auditLogExportWritesAnEmptyOptionalWithoutFailing() {
        assertThatCode(() -> ObjectMapperFactory.auditLogExport().writeValueAsString(Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void auditLogExportWritesDatesAsTextAndDropsNullMembers() throws JsonProcessingException {
        Map<String, Object> additionalData = new LinkedHashMap<>();
        additionalData.put("at", OffsetDateTime.of(2024, 5, 6, 7, 8, 9, 0, ZoneOffset.UTC));
        additionalData.put("absent", null);

        String json = ObjectMapperFactory.auditLogExport().writeValueAsString(additionalData);

        assertThat(json).isEqualTo("{\"at\":\"2024-05-06T07:08:09Z\"}");
    }

    /**
     * Guards the reason the export keeps its own recipe. An explicit module list suppresses discovery, and the export
     * would record {@code ERROR_SERIALIZATION} instead of data.
     */
    @Test
    void wireDoesNotRegisterTheDiscoveredJdk8Module() {
        ObjectMapper wire = ObjectMapperFactory.wire();

        assertThatThrownBy(() -> wire.writeValueAsString(Map.of("present", Optional.of("value"))))
                .isInstanceOf(InvalidDefinitionException.class);
    }
}
