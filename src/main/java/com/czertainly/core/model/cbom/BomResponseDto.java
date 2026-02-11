package com.czertainly.core.model.cbom;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing (C)BOM")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BomResponseDto extends HashMap<String, Object> {
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("specVersion", get("specVersion"))
                .append("serialNumber", get("serialNumber"))
                .append("version", get("version"))
                .toString();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getComponents() {
        Object components = this.get("components");
        if (components instanceof List) {
            return (List<Map<String, Object>>) components;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadata() {
        Object metadata = this.get("metadata");
        if (metadata instanceof Map) {
            return (Map<String, Object>) metadata;
        }
        return null;
    }

    public CbomDetailDto mapToCbomDetailDto() {

        CbomDetailDto cbomDetailDto = new CbomDetailDto();
        
        // Set raw content
        cbomDetailDto.setContent(this);
        
        // Extract basic fields
        cbomDetailDto.setSerialNumber((String) this.get("serialNumber"));
        cbomDetailDto.setVersion(String.valueOf(this.get("version")));
        cbomDetailDto.setSpecVersion((String) this.get("specVersion"));
        
        // Parse timestamp
        Object timestamp = this.get("timestamp");
        if (timestamp != null) {
            cbomDetailDto.setTimestamp(parseTimestamp(timestamp.toString()));
        }
        
        // Extract metadata for source
        Map<String, Object> metadata = this.getMetadata();
        if (metadata != null) {
            List<Map<String, Object>> tools = (List<Map<String, Object>>) metadata.get("tools");
            if (tools != null && !tools.isEmpty()) {
                Map<String, Object> tool = tools.get(0);
                String toolName = (String) tool.get("name");
                String toolVersion = (String) tool.get("version");
                cbomDetailDto.setSource(toolName != null ? toolName + (toolVersion != null ? " " + toolVersion : "") : null);
            }
        }
        
        // Count components by type
        List<Map<String, Object>> components = this.getComponents();
        if (components != null) {
            int algorithms = 0;
            int certificates = 0;
            int protocols = 0;
            int cryptoMaterial = 0;
            
            for (Map<String, Object> component : components) {
                String type = (String) component.get("type");
                if (type != null) {
                    switch (type) {
                        case "cryptographic-asset":
                            Map<String, Object> cryptoProperties = (Map<String, Object>) component.get("cryptoProperties");
                            if (cryptoProperties != null) {
                                String assetType = (String) cryptoProperties.get("assetType");
                                if ("algorithm".equalsIgnoreCase(assetType)) {
                                    algorithms++;
                                } else if ("certificate".equalsIgnoreCase(assetType)) {
                                    certificates++;
                                } else if ("protocol".equalsIgnoreCase(assetType)) {
                                    protocols++;
                                }
                            }
                            cryptoMaterial++;
                            break;
                    }
                }
            }
            
            cbomDetailDto.setAlgorithms(algorithms);
            cbomDetailDto.setCertificates(certificates);
            cbomDetailDto.setProtocols(protocols);
            cbomDetailDto.setCryptoMaterial(cryptoMaterial);
            cbomDetailDto.setTotalAssets(components.size());
        } else {
            cbomDetailDto.setAlgorithms(0);
            cbomDetailDto.setCertificates(0);
            cbomDetailDto.setProtocols(0);
            cbomDetailDto.setCryptoMaterial(0);
            cbomDetailDto.setTotalAssets(0);
        }
        
        return cbomDetailDto;
    }
    
    private OffsetDateTime parseTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}


