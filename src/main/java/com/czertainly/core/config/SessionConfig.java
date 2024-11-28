package com.czertainly.core.config;

import com.czertainly.core.service.SettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Date;

@Configuration
@EnableJdbcHttpSession(tableName = "${DB_SCHEMA:core}.spring_session")
public class SessionConfig implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Bean("springSessionConversionService")
    public GenericConversionService springSessionConversionService(ObjectMapper objectMapper) {
        ObjectMapper copy = objectMapper.copy();
        // Register Spring Security Jackson Modules
        copy.registerModules(SecurityJackson2Modules.getModules(this.classLoader));
        // Activate default typing explicitly if not using Spring Security
        GenericConversionService converter = new GenericConversionService();
        converter.addConverter(Object.class, byte[].class, new SerializingConverter(new JsonSerializer(copy)));
        converter.addConverter(byte[].class, Object.class, new DeserializingConverter(new JsonDeserializer(copy)));
        return converter;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    static class JsonSerializer implements Serializer<Object> {

        private final ObjectMapper objectMapper;

        JsonSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void serialize(Object object, OutputStream outputStream) throws IOException {
            this.objectMapper.writeValue(outputStream, object);
        }

    }

    static class JsonDeserializer implements Deserializer<Object> {

        private final ObjectMapper objectMapper;

        JsonDeserializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public Object deserialize(InputStream inputStream) throws IOException {
            return this.objectMapper.readValue(inputStream, Object.class);
        }

    }

    private static final String CREATE_SESSION_ATTRIBUTE_QUERY = """
            INSERT INTO %TABLE_NAME%_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
            VALUES (?, ?, encode(?, 'escape')::jsonb)
            """;

    private static final String UPDATE_SESSION_ATTRIBUTE_QUERY = """
            UPDATE %TABLE_NAME%_ATTRIBUTES
            SET ATTRIBUTE_BYTES = encode(?, 'escape')::jsonb
            WHERE SESSION_PRIMARY_ID = ?
            AND ATTRIBUTE_NAME = ?
            """;

    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> customizer() {
        return sessionRepository -> {
            sessionRepository.setCreateSessionAttributeQuery(CREATE_SESSION_ATTRIBUTE_QUERY);
            sessionRepository.setUpdateSessionAttributeQuery(UPDATE_SESSION_ATTRIBUTE_QUERY);
        };
    }

}


