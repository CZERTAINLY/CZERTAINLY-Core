package com.czertainly.core.util;

import com.czertainly.api.model.core.certificate.CertificateValidationDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetaDefinitions {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static String serialize(Map<String, Object> metaData) {
		try {
			return OBJECT_MAPPER.writeValueAsString(metaData);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static Map<String, Object> deserialize(String metaJson) {
		if (metaJson == null || metaJson.isEmpty()) {
			return new HashMap<>();
		}
		try {
			return OBJECT_MAPPER.readValue(metaJson, new TypeReference<>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static String serializeValidation(Map<String, CertificateValidationDto> metaData) {
		try {
			return OBJECT_MAPPER.writeValueAsString(metaData);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static Map<String, CertificateValidationDto> deserializeValidation(String metaJson) {
		if (metaJson == null || metaJson.isEmpty()) {
			return new HashMap<>();
		}
		try {
			return OBJECT_MAPPER.readValue(metaJson, new TypeReference<>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static String serializeArrayString(List<String> metaData) {
		try {
			return OBJECT_MAPPER.writeValueAsString(metaData);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static List<String> deserializeArrayString(String metaJson) {
		if (metaJson == null || metaJson.isEmpty()) {
			return new ArrayList<>();
		}
		try {
			return OBJECT_MAPPER.readValue(metaJson, new TypeReference<>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
