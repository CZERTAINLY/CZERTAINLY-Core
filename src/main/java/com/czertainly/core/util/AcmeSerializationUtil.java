package com.czertainly.core.util;

import com.czertainly.api.model.core.acme.Identifier;
import com.czertainly.api.model.core.certificate.CertificateValidationDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcmeSerializationUtil {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static String serializeIdentifiers(List<Identifier> identifiers) {
		try {
			return OBJECT_MAPPER.writeValueAsString(identifiers);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static List<Identifier> deserializeIdentifiers(String identifier) {
		if (identifier == null || identifier.isEmpty()) {
			return new ArrayList<>();
		}
		try {
			return OBJECT_MAPPER.readValue(identifier, new TypeReference<>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static Identifier deserializeIdentifier(String identifier) {
		if (identifier == null || identifier.isEmpty()) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(identifier, new TypeReference<>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static String serialize(Object object) {
		try {
			return OBJECT_MAPPER.writeValueAsString(object);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static Object deserialize(String object) {
		if (object == null || object.isEmpty()) {
			return new ArrayList<>();
		}
		try {
			return OBJECT_MAPPER.readValue(object, new TypeReference<>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}



}
