package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.core.config.WebAppConfig;
import com.otilm.core.util.AcmeJsonProcessor;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Supplies the exact serializer production uses on each surface. The platform has three and they disagree, so a golden
 * produced by the wrong one baselines a shape production never emits.
 */
final class GoldenMappers {

    private GoldenMappers() {
    }

    /**
     * The wire mapper: every {@code @RestController} response body, plus {@code ObjectToJsonConverter} and
     * {@code OutboundSecretContainment}. Built through the factory method Spring itself calls, which takes no
     * collaborators and so needs no application context.
     */
    static ObjectMapper web() {
        return new WebAppConfig().jsonObjectMapper();
    }

    /**
     * Hibernate's format mapper: every {@code @JdbcTypeCode(SqlTypes.JSON)} column.
     * <p>
     * Spring's {@code ObjectMapper} reaches Hibernate only through a {@code HibernatePropertiesCustomizer}, and the
     * repository's only one is {@code @Profile("test")}. Production therefore persists with Jackson's defaults, which
     * is what these goldens baseline.
     */
    static JacksonJsonFormatMapper hibernateJson() {
        return new JacksonJsonFormatMapper();
    }

    /**
     * The {@code AcmeJsonProcessor} mapper, which parses the outer JWS envelope in {@code AcmeJwsRequest}. Read
     * reflectively so a reconfiguration of the production field cannot leave these assertions green.
     */
    static ObjectMapper acmeJwsEnvelope() {
        ObjectMapper productionMapper = (ObjectMapper) ReflectionTestUtils
                .getField(AcmeJsonProcessor.class, "OBJECT_MAPPER");

        if (productionMapper == null) {
            throw new IllegalStateException("AcmeJsonProcessor.OBJECT_MAPPER is gone; the ACME envelope goldens are "
                    + "pinning a mapper production no longer has");
        }
        return productionMapper;
    }
}
