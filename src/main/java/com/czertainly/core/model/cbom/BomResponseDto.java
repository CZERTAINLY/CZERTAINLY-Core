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

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append(FIELD_SPEC_VERSION, get(FIELD_SPEC_VERSION))
                .append(FIELD_SERIAL_NUMBER, get(FIELD_SERIAL_NUMBER))
                .append(FIELD_VERSION, get(FIELD_VERSION))
                .toString();
    }
}
