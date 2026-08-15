package com.otilm.core.config;

import com.otilm.core.serialization.ObjectMapperFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * States the {@code FormatMapper} for every {@code @JdbcTypeCode(SqlTypes.JSON)} column, so tests and production
 * serialize them identically. {@link ObjectMapperFactory#jsonColumn()} matches what Hibernate builds by default, so no
 * persisted byte changes.
 */
@Configuration
public class JsonColumnFormatMapperConfig {

    @Bean
    JacksonJsonFormatMapper jsonColumnFormatMapper() {
        return new JacksonJsonFormatMapper(ObjectMapperFactory.jsonColumn());
    }

    @Bean
    HibernatePropertiesCustomizer jsonColumnFormatMapperCustomizer(JacksonJsonFormatMapper jsonColumnFormatMapper) {
        return properties -> properties.put(AvailableSettings.JSON_FORMAT_MAPPER, jsonColumnFormatMapper);
    }
}
