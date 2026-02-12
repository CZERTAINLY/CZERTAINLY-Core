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

    @Test
    @DisplayName("Should return null when components field is missing")
    void testGetComponents_WhenMissing() {
        List<Map<String, Object>> components = bomResponseDto.getComponents();

        assertNull(components);
    }

    @Test
    @DisplayName("Should return null when components field is not a list")
    void testGetComponents_WhenNotList() {
        bomResponseDto.put("components", "not a list");

        List<Map<String, Object>> components = bomResponseDto.getComponents();

        assertNull(components);
    }

    @Test
    @DisplayName("Should return components list when present")
    void testGetComponents_WhenPresent() {
        List<Map<String, Object>> expectedComponents = new ArrayList<>();
        Map<String, Object> component = new HashMap<>();
        component.put("type", "cryptographic-asset");
        expectedComponents.add(component);

        bomResponseDto.put("components", expectedComponents);

        List<Map<String, Object>> components = bomResponseDto.getComponents();

        assertNotNull(components);
        assertEquals(1, components.size());
        assertEquals(expectedComponents, components);
    }

    @Test
    @DisplayName("Should return null when metadata field is missing")
    void testGetMetadata_WhenMissing() {
        Map<String, Object> metadata = bomResponseDto.getMetadata();

        assertNull(metadata);
    }

    @Test
    @DisplayName("Should return null when metadata field is not a map")
    void testGetMetadata_WhenNotMap() {
        bomResponseDto.put("metadata", "not a map");

        Map<String, Object> metadata = bomResponseDto.getMetadata();

        assertNull(metadata);
    }

    @Test
    @DisplayName("Should return metadata map when present")
    void testGetMetadata_WhenPresent() {
        Map<String, Object> expectedMetadata = new HashMap<>();
        expectedMetadata.put("timestamp", "2024-01-01T00:00:00Z");

        bomResponseDto.put("metadata", expectedMetadata);

        Map<String, Object> metadata = bomResponseDto.getMetadata();

        assertNotNull(metadata);
        assertEquals(expectedMetadata, metadata);
    }

    @Test
    @DisplayName("Should map basic fields to CbomDetailDto")
    void testMapToCbomDetailDto_BasicFields() {
        bomResponseDto.put("specVersion", "1.5");
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertNotNull(result);
        assertEquals("1.5", result.getSpecVersion());
        assertEquals("urn:uuid:test-123", result.getSerialNumber());
        assertEquals("1", result.getVersion());
        assertEquals(bomResponseDto, result.getContent());
    }

    @Test
    @DisplayName("Should parse ISO 8601 timestamp")
    void testMapToCbomDetailDto_WithTimestamp() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);
        bomResponseDto.put("timestamp", "2024-01-15T10:30:00Z");

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertNotNull(result.getTimestamp());
        assertEquals(2024, result.getTimestamp().getYear());
        assertEquals(1, result.getTimestamp().getMonthValue());
        assertEquals(15, result.getTimestamp().getDayOfMonth());
    }

    @Test
    @DisplayName("Should parse timestamp with timezone offset")
    void testMapToCbomDetailDto_WithTimezoneOffset() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);
        bomResponseDto.put("timestamp", "2024-01-15T10:30:00+02:00");

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertNotNull(result.getTimestamp());
        assertEquals(ZoneOffset.of("+02:00"), result.getTimestamp().getOffset());
    }

    @Test
    @DisplayName("Should handle null timestamp")
    void testMapToCbomDetailDto_WithNullTimestamp() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertNull(result.getTimestamp());
    }

    @Test
    @DisplayName("Should extract source from metadata tools")
    void testMapToCbomDetailDto_WithMetadataTools() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        Map<String, Object> tool = new HashMap<>();
        tool.put("name", "CZERTAINLY");
        tool.put("version", "2.0.0");

        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tools", tools);

        bomResponseDto.put("metadata", metadata);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertNotNull(result.getSource());
        assertEquals("CZERTAINLY 2.0.0", result.getSource());
    }

    @Test
    @DisplayName("Should handle tool without version")
    void testMapToCbomDetailDto_WithToolWithoutVersion() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        Map<String, Object> tool = new HashMap<>();
        tool.put("name", "CZERTAINLY");

        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tools", tools);

        bomResponseDto.put("metadata", metadata);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals("CZERTAINLY", result.getSource());
    }

    @Test
    @DisplayName("Should handle empty tools list")
    void testMapToCbomDetailDto_WithEmptyTools() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tools", new ArrayList<>());

        bomResponseDto.put("metadata", metadata);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertNull(result.getSource());
    }

    @Test
    @DisplayName("Should handle null metadata")
    void testMapToCbomDetailDto_WithNullMetadata() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertNull(result.getSource());
        assertEquals(0, result.getAlgorithms());
        assertEquals(0, result.getCertificates());
        assertEquals(0, result.getProtocols());
        assertEquals(0, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should count algorithm assets")
    void testMapToCbomDetailDto_CountAlgorithms() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        Map<String, Object> component1 = createCryptoComponent("algorithm");
        Map<String, Object> component2 = createCryptoComponent("algorithm");
        
        components.add(component1);
        components.add(component2);

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(2, result.getAlgorithms());
        assertEquals(2, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should count certificate assets")
    void testMapToCbomDetailDto_CountCertificates() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        Map<String, Object> component1 = createCryptoComponent("certificate");
        Map<String, Object> component2 = createCryptoComponent("certificate");
        Map<String, Object> component3 = createCryptoComponent("certificate");
        
        components.add(component1);
        components.add(component2);
        components.add(component3);

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(3, result.getCertificates());
        assertEquals(3, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should count protocol assets")
    void testMapToCbomDetailDto_CountProtocols() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        Map<String, Object> component = createCryptoComponent("protocol");
        components.add(component);

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(1, result.getProtocols());
        assertEquals(1, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should count mixed asset types")
    void testMapToCbomDetailDto_CountMixedAssets() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        components.add(createCryptoComponent("algorithm"));
        components.add(createCryptoComponent("algorithm"));
        components.add(createCryptoComponent("certificate"));
        components.add(createCryptoComponent("protocol"));
        
        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(2, result.getAlgorithms());
        assertEquals(1, result.getCertificates());
        assertEquals(1, result.getProtocols());
        assertEquals(4, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should handle components with non-cryptographic type")
    void testMapToCbomDetailDto_WithNonCryptoComponents() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        Map<String, Object> component = new HashMap<>();
        component.put("type", "library");
        components.add(component);
        
        components.add(createCryptoComponent("algorithm"));

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(1, result.getAlgorithms());
        assertEquals(0, result.getCertificates());
        assertEquals(0, result.getProtocols());
        assertEquals(1, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should handle cryptographic asset without cryptoProperties")
    void testMapToCbomDetailDto_WithoutCryptoProperties() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        Map<String, Object> component = new HashMap<>();
        component.put("type", "cryptographic-asset");
        components.add(component);

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(0, result.getAlgorithms());
        assertEquals(0, result.getCertificates());
        assertEquals(0, result.getProtocols());
        assertEquals(1, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should handle cryptographic asset with null assetType")
    void testMapToCbomDetailDto_WithNullAssetType() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        Map<String, Object> component = new HashMap<>();
        component.put("type", "cryptographic-asset");
        
        Map<String, Object> cryptoProperties = new HashMap<>();
        component.put("cryptoProperties", cryptoProperties);
        
        components.add(component);

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(0, result.getAlgorithms());
        assertEquals(0, result.getCertificates());
        assertEquals(0, result.getProtocols());
        assertEquals(1, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should handle unknown asset type")
    void testMapToCbomDetailDto_WithUnknownAssetType() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        Map<String, Object> component = new HashMap<>();
        component.put("type", "cryptographic-asset");
        
        Map<String, Object> cryptoProperties = new HashMap<>();
        cryptoProperties.put("assetType", "unknown-type");
        component.put("cryptoProperties", cryptoProperties);
        
        components.add(component);

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(0, result.getAlgorithms());
        assertEquals(0, result.getCertificates());
        assertEquals(0, result.getProtocols());
        assertEquals(1, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should be case insensitive for asset types")
    void testMapToCbomDetailDto_CaseInsensitiveAssetTypes() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        List<Map<String, Object>> components = new ArrayList<>();
        
        components.add(createCryptoComponent("ALGORITHM"));
        components.add(createCryptoComponent("Certificate"));
        components.add(createCryptoComponent("ProToCoL"));

        bomResponseDto.put("components", components);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(1, result.getAlgorithms());
        assertEquals(1, result.getCertificates());
        assertEquals(1, result.getProtocols());
        assertEquals(3, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should handle empty components list")
    void testMapToCbomDetailDto_WithEmptyComponents() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);
        bomResponseDto.put("components", new ArrayList<>());

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(0, result.getAlgorithms());
        assertEquals(0, result.getCertificates());
        assertEquals(0, result.getProtocols());
        assertEquals(0, result.getCryptoMaterial());
    }

    @Test
    @DisplayName("Should handle null components")
    void testMapToCbomDetailDto_WithNullComponents() {
        bomResponseDto.put("serialNumber", "urn:uuid:test-123");
        bomResponseDto.put("version", 1);

        CbomDetailDto result = bomResponseDto.mapToCbomDetailDto();

        assertEquals(0, result.getAlgorithms());
        assertEquals(0, result.getCertificates());
        assertEquals(0, result.getProtocols());
        assertEquals(0, result.getCryptoMaterial());
    }

    // Helper method to create cryptographic components
    private Map<String, Object> createCryptoComponent(String assetType) {
        Map<String, Object> component = new HashMap<>();
        component.put("type", "cryptographic-asset");
        
        Map<String, Object> cryptoProperties = new HashMap<>();
        cryptoProperties.put("assetType", assetType);
        component.put("cryptoProperties", cryptoProperties);
        
        return component;
    }
}

