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

    private static final String FIELD_SPEC_VERSION = "specVersion";
    private static final String FIELD_SERIAL_NUMBER = "serialNumber";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_COMPONENTS = "components";
    private static final String FIELD_METADATA = "metadata";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_CRYPTO_PROPERTIES = "cryptoProperties";
    private static final String FIELD_ASSET_TYPE = "assetType";

    private static final String TYPE_CRYPTOGRAPHIC_ASSET = "cryptographic-asset";
    private static final String ASSET_TYPE_TOTAL = "total";
    private static final String ASSET_TYPE_ALGORITHM = "algorithm";
    private static final String ASSET_TYPE_CERTIFICATE = "certificate";
    private static final String ASSET_TYPE_PROTOCOL = "protocol";

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append(FIELD_SPEC_VERSION, get(FIELD_SPEC_VERSION))
                .append(FIELD_SERIAL_NUMBER, get(FIELD_SERIAL_NUMBER))
                .append(FIELD_VERSION, get(FIELD_VERSION))
                .toString();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getComponents() {
        Object components = this.get(FIELD_COMPONENTS);
        if (components instanceof List) {
            return (List<Map<String, Object>>) components;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadata() {
        Object metadata = this.get(FIELD_METADATA);
        if (metadata instanceof Map) {
            return (Map<String, Object>) metadata;
        }
        return null;
    }

    public CbomDetailDto mapToCbomDetailDto() {

        CbomDetailDto cbomDetailDto = new CbomDetailDto();

        cbomDetailDto.setContent(this);
        cbomDetailDto.setSerialNumber((String) this.get(FIELD_SERIAL_NUMBER));
        cbomDetailDto.setVersion(String.valueOf(this.get(FIELD_VERSION)));
        cbomDetailDto.setSpecVersion((String) this.get(FIELD_SPEC_VERSION));
        Object timestamp = this.get(FIELD_TIMESTAMP);
        if (timestamp != null) {
            cbomDetailDto.setTimestamp(parseTimestamp(timestamp.toString()));
        }

        Map<String, Object> metadata = this.getMetadata();
        if (metadata != null) {
            List<Map<String, Object>> tools = (List<Map<String, Object>>) metadata.get(FIELD_TOOLS);
            if (tools != null && !tools.isEmpty()) {
                Map<String, Object> tool = tools.get(0);
                String toolName = (String) tool.get(FIELD_NAME);
                String toolVersion = (String) tool.get(FIELD_VERSION);
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
            int totalAssetsCount = 0;
            
            for (Map<String, Object> component : components) {
                String type = (String) component.get(FIELD_TYPE);
                if (type != null) {
                    switch (type) {
                        case TYPE_CRYPTOGRAPHIC_ASSET:
                            Map<String, Object> cryptoProperties = (Map<String, Object>) component.get(FIELD_CRYPTO_PROPERTIES);
                            if (cryptoProperties.get(ASSET_TYPE_TOTAL) != null) {
                                totalAssetsCount = (int) cryptoProperties.get(ASSET_TYPE_TOTAL);
                            }
                            if (cryptoProperties != null) {
                                String assetType = (String) cryptoProperties.get(FIELD_ASSET_TYPE);
                                if (ASSET_TYPE_ALGORITHM.equalsIgnoreCase(assetType)) {
                                    algorithms++;
                                } else if (ASSET_TYPE_CERTIFICATE.equalsIgnoreCase(assetType)) {
                                    certificates++;
                                } else if (ASSET_TYPE_PROTOCOL.equalsIgnoreCase(assetType)) {
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
            cbomDetailDto.setTotalAssets(totalAssetsCount);
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
