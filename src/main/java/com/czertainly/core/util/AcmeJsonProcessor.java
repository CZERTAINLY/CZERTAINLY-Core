package com.czertainly.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class contains the static method for processing the JSON entities from
 * ACME request.
 */
public class AcmeJsonProcessor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(AcmeJsonProcessor.class);
    /**
     * Class to parse the information from source JSON to the requested format
     * @param sourceJson Source JSON
     * @param <T> Generic Type
     * @return JSON Objected mapped to the class required
     */
    public static <T> T generalBodyJsonParser(String sourceJson, Class<T> tClass) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(sourceJson, tClass);
    }

    public static <T> T getPayloadAsRequestObject(JWSObject jwsObject, Class<T> returnType) {

        String serializedData = AcmeSerializationUtil.serialize(jwsObject.getPayload().toJSONObject());
        return (T) AcmeSerializationUtil.deserialize(serializedData, returnType);
    }


}
