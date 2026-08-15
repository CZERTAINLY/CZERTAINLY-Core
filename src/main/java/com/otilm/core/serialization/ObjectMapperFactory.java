package com.otilm.core.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Factory for every kind of {@link ObjectMapper} in the platform, centralized at one place.
 */
public final class ObjectMapperFactory {

    private ObjectMapperFactory() {
    }

    /**
     * The wire recipe: HTTP request and response bodies, and anything shaped to match them. Exposed as the
     * {@code jacksonObjectMapper} bean, which callers inside the container should inject rather than call this.
     */
    public static ObjectMapper wire() {
        return Jackson2ObjectMapperBuilder
                .json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY)
                .modules(new JavaTimeModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    /**
     * The audit-log recipe, pinned as the audit subsystem has always built it rather than derived from {@link #wire()}.
     * Null members survive, because an audit line records that a member was absent.
     */
    public static ObjectMapper auditLog() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * The audit-export recipe, pinned as the export has always built it. It discovers every module on the classpath,
     * because the exported columns are open-typed and may hold any producer's JDK8 value.
     */
    public static ObjectMapper auditLogExport() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /**
     * Jackson's own defaults, for code that serializes JSON by hand before storing it. No migration guards persisted
     * JSON, so a shape change here splits a table into rows written before it and rows written after.
     *
     * @see #jsonColumn()
     */
    public static ObjectMapper storage() {
        return new ObjectMapper();
    }

    /**
     * The recipe for every {@code @JdbcTypeCode(SqlTypes.JSON)} column, matching what Hibernate builds by default: a
     * plain {@code ObjectMapper} carrying the classpath modules. The modules are load-bearing, as
     * {@code JavaTimeModule} governs the shape of every persisted date.
     */
    public static ObjectMapper jsonColumn() {
        return new ObjectMapper().registerModules(ObjectMapper.findModules(ObjectMapperFactory.class.getClassLoader()));
    }

    /**
     * {@link #storage()} for payloads produced elsewhere, where an unrecognized member is expected rather than
     * exceptional.
     */
    public static ObjectMapper lenientStorage() {
        return storage().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * {@link #lenientStorage()} for utilities that serialize whatever object they are handed, where a value with no
     * visible properties is written as an empty object rather than failing.
     */
    public static ObjectMapper emptyBeanTolerantStorage() {
        return lenientStorage().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * The ACME-request recipe. An unrecognised member is rejected, because RFC 8555 request bodies are exact.
     */
    public static ObjectMapper acmeRequest() {
        return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    /**
     * The attribute-content recipe. Unknown members are tolerated, because a definition may be written by an older
     * connector version than the one reading it.
     */
    public static ObjectMapper attributeContent() {
        return JsonMapper
                .builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * The secret-thumbprint recipe. The ordering settings are load-bearing: the serialized bytes are hashed, so a
     * different key order would rewrite every stored thumbprint.
     */
    public static ObjectMapper secretThumbprint() {
        return JsonMapper
                .builder()
                .findAndAddModules()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }

    /**
     * The JMS-message recipe. It takes the container's builder as a parameter, which a static context cannot reach, so
     * that Spring Boot's Jackson autoconfiguration still applies.
     */
    public static ObjectMapper jmsMessage(Jackson2ObjectMapperBuilder builder) {
        return builder
                .createXmlMapper(false)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()
                .findAndRegisterModules();
    }

    /**
     * The CSV-export recipe, here because a {@link CsvMapper} carries the same Jackson defaults as any other mapper. It
     * is returned as its own type because callers need {@code schemaFor}.
     */
    public static CsvMapper csvExport() {
        CsvMapper mapper = new CsvMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(CsvGenerator.Feature.ESCAPE_QUOTE_CHAR_WITH_ESCAPE_CHAR);
        return mapper;
    }
}
