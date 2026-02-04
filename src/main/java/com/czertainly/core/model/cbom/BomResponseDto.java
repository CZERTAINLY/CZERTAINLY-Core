package com.czertainly.core.model.cbom;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "Response containing (C)BOM")
public class BomResponseDto {

    @NotNull
    @Schema(description = "The CBOM document in JSON format", requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode bom;

    @Override
    public String toString() {
        JsonNode bom2 = (bom == null) ? JsonNodeFactory.instance.objectNode() : bom;
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("specVersion", bom2.at("/properties/specVersion").asText("N/A"))
                .append("serialNumber", bom2.at("/properties/serialNumber").asText("N/A"))
                .append("version", bom2.at("/properties/version").asText("N/A"))
                .toString();
    }

}


