package com.czertainly.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.security.jackson2.SecurityJackson2Modules;

@Configuration
public class SessionConfig {
    // Empty class to disable JDBC Session Config in tests

    @Bean("springSessionConversionService")
    public GenericConversionService springSessionConversionService(ObjectMapper objectMapper) {
        ObjectMapper copy = objectMapper.copy();
        copy.registerModules(SecurityJackson2Modules.getModules(this.getClass().getClassLoader()));
        GenericConversionService converter = new GenericConversionService();
        converter.addConverter(Object.class, byte[].class, new SerializingConverter(new JsonSerializer(copy)));
        converter.addConverter(byte[].class, Object.class, new DeserializingConverter(new JsonDeserializer(copy)));
        return converter;
    }

    static class JsonSerializer implements Serializer<Object> {
        private final ObjectMapper objectMapper;
        JsonSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }
        @Override
        public void serialize(Object object, java.io.OutputStream outputStream) throws java.io.IOException {
            this.objectMapper.writeValue(outputStream, object);
        }
    }

    static class JsonDeserializer implements Deserializer<Object> {
        private final ObjectMapper objectMapper;
        JsonDeserializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }
        @Override
        public Object deserialize(java.io.InputStream inputStream) throws java.io.IOException {
            return this.objectMapper.readValue(inputStream, Object.class);
        }
    }
}
