package com.czertainly.core.model.cbom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Setter
@Getter
@Schema(description = "Response containing BOM metadata and statistics")
public class BomResponseDto {

    @NotNull
    @Schema(
            description = "CycloneDX serial number (URN, RFC-4122)",
            example = "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String serialNumber;

    @Schema(
            description = "CycloneDX integer version number for the CBOM document",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer version;

    @NotNull
    @Valid
    @Schema(description = "CBOM statistics", requiredMode = Schema.RequiredMode.REQUIRED)
    private BomStatsDto stats;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("serialNumber", serialNumber)
        .append("version", version)
        .append("stats", stats)
        .toString();
    }
}


