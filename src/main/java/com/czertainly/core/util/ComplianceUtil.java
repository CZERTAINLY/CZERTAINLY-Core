package com.czertainly.core.util;

import com.czertainly.api.model.core.certificate.CertificateComplianceResultDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class ComplianceUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String serializeComplianceResult(List<CertificateComplianceResultDto> result) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(result);
    }

    public static List<CertificateComplianceResultDto> deserializeComplianceResult(String result) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(result, new TypeReference<>() {
        });
    }
}
