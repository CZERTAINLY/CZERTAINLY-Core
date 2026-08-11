package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.core.config.WebAppConfig;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

/**
 * Supplies the exact serializers production uses on each surface.
 * <p>
 * Getting this right is the whole premise of the suite: a golden produced by a mapper nothing actually uses baselines a
 * shape production never emits, and would then report "no drift" straight through a real regression. The platform has
 * <b>three</b> distinct JSON writers, and which one applies depends on the surface, not the type.
 */
final class GoldenMappers {

    private GoldenMappers() {
    }

    /**
     * The application's wire mapper — everything that leaves through an HTTP response body.
     * <p>
     * Built through the very same factory method Spring calls. {@code WebAppConfig#jsonObjectMapper} takes no
     * collaborators, so invoking it directly yields a byte-identical mapper with no application context, which is what
     * keeps this suite off the test-context budget.
     * <p>
     * {@code WebAppConfig#configureMessageConverters} registers it as the {@code MappingJackson2HttpMessageConverter}
     * mapper, so it serializes <i>every</i> {@code @RestController} return value — REST DTOs and ACME protocol
     * documents alike. Its configuration is what these goldens pin: ISO-8601 dates rather than numeric timestamps, the
     * {@code JavaTimeModule}, {@code NON_NULL} inclusion, and lenient missing-external-type-id handling.
     * <p>
     * It is also the mapper injected into {@code ObjectToJsonConverter} and {@code OutboundSecretContainment}.
     */
    static ObjectMapper web() {
        return new WebAppConfig().jsonObjectMapper();
    }

    /**
     * Hibernate's JSON format mapper — everything persisted into a {@code jsonb} column through
     * {@code @JdbcTypeCode(SqlTypes.JSON)}.
     * <p>
     * This is <b>not</b> the wire mapper, and the difference is not cosmetic. Spring's {@code ObjectMapper} bean
     * reaches Hibernate only through a {@code HibernatePropertiesCustomizer} that sets
     * {@code AvailableSettings.JSON_FORMAT_MAPPER}, and production registers none — the only such customizer in the
     * repository is {@code JsonFormatMapperTestConfig}, annotated {@code @Profile("test")}. With that setting unset
     * Hibernate constructs this class itself, and its no-argument constructor builds a plain {@code ObjectMapper} with
     * {@code findModules()} auto-discovery and no further configuration. A persisted column therefore gets Jackson's
     * <i>defaults</i> — numeric timestamps, nulls included — the opposite of the wire mapper on both counts.
     * <p>
     * A consequence worth stating plainly: because that customizer is test-profile-only, integration tests write
     * {@code jsonb} through the wire mapper while production writes it through this one. Baselining columns against the
     * wire mapper would pin a shape production never emits. Tracked as OmniTrustILM/core#2000.
     */
    static JacksonJsonFormatMapper hibernateJson() {
        return new JacksonJsonFormatMapper();
    }

    /**
     * The bare mapper held by {@code AcmeJsonProcessor}, which serves exactly one call in the whole codebase:
     * {@code generalBodyJsonParser(request, JwsBody.class)} in {@code AcmeJwsRequest}, parsing the outer JWS envelope
     * of an inbound ACME request.
     * <p>
     * It is deliberately <b>not</b> the mapper for ACME protocol documents. Those are returned as
     * {@code ResponseEntity} bodies from {@code AcmeControllerImpl} and so are serialized by {@link #web()}, while
     * inbound ACME payloads are decoded by {@code AcmeJsonProcessor#getPayloadAsRequestObject}, which delegates to yet
     * another private mapper inside {@code SerializationUtil}. Reaching for this mapper to baseline a wire document
     * would pin a shape nothing produces — the mistake this javadoc exists to prevent.
     */
    static ObjectMapper acmeJwsEnvelope() {
        return new ObjectMapper();
    }
}
