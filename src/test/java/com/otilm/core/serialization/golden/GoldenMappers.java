package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.core.config.WebAppConfig;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

/**
 * Supplies the exact serializer production uses on each surface. The platform has three and they disagree, so a golden
 * produced by the wrong one baselines a shape production never emits and reports "no drift" through a real regression.
 */
final class GoldenMappers {

    private GoldenMappers() {
    }

    /**
     * The wire mapper: every {@code @RestController} response body (REST DTOs and ACME documents alike), plus
     * {@code ObjectToJsonConverter} and {@code OutboundSecretContainment}. ISO-8601 dates, {@code JavaTimeModule},
     * {@code NON_NULL} inclusion.
     * <p>
     * Built through the factory method Spring itself calls; it takes no collaborators, so invoking it directly yields
     * an identical mapper with no application context.
     */
    static ObjectMapper web() {
        return new WebAppConfig().jsonObjectMapper();
    }

    /**
     * Hibernate's format mapper: every {@code @JdbcTypeCode(SqlTypes.JSON)} column.
     * <p>
     * Spring's {@code ObjectMapper} bean reaches Hibernate only through a {@code HibernatePropertiesCustomizer} setting
     * {@code AvailableSettings.JSON_FORMAT_MAPPER}, and the only one in the repository is
     * {@code JsonFormatMapperTestConfig}, {@code @Profile("test")}. Production therefore persists with Jackson's
     * defaults — numeric timestamps, nulls included — while integration tests write {@code jsonb} through the wire
     * mapper. Tracked as OmniTrustILM/core#2000; these goldens baseline production.
     */
    static JacksonJsonFormatMapper hibernateJson() {
        return new JacksonJsonFormatMapper();
    }

    /**
     * The bare {@code AcmeJsonProcessor} mapper, which serves exactly one call in the codebase: parsing the outer JWS
     * envelope in {@code AcmeJwsRequest}. It is <b>not</b> the mapper for ACME protocol documents — those go out as
     * {@code ResponseEntity} bodies through {@link #web()}.
     */
    static ObjectMapper acmeJwsEnvelope() {
        return new ObjectMapper();
    }
}
