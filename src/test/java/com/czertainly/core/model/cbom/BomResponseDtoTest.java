package com.czertainly.core.model.cbom;

import com.czertainly.api.model.core.cbom.CbomDetailDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneOffset;
import java.util.*;


@DisplayName("BomResponseDto Tests")
class BomResponseDtoTest {

    private BomResponseDto bomResponseDto;

    @BeforeEach
    void setUp() {
        bomResponseDto = new BomResponseDto();
    }

    @Test
    @DisplayName("Should inherit from HashMap")
    void testInheritance() {
        assertTrue(bomResponseDto instanceof HashMap);
        assertTrue(bomResponseDto instanceof Map);
    }

    @Test
    @DisplayName("Should allow putting and getting values")
    void testBasicMapOperations() {
        bomResponseDto.put("specVersion", "1.5");
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        assertEquals("1.5", bomResponseDto.get("specVersion"));
        assertEquals("urn:uuid:test-123", bomResponseDto.get("serialNumber"));
        assertEquals(1, bomResponseDto.get("version"));
    }

    @Test
    @DisplayName("Should return string representation with key fields")
    void testToString() {
        bomResponseDto.put("specVersion", "1.5");
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", "1");

        String result = bomResponseDto.toString();

        assertNotNull(result);
        assertTrue(result.contains("specVersion"));
        assertTrue(result.contains("serialNumber"));
        assertTrue(result.contains("version"));
    }
}

