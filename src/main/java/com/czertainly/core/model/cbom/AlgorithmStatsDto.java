package com.czertainly.core.model.cbom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import jakarta.validation.constraints.NotNull;

@Setter
@Getter
@Schema(description = "Algorithm usage statistics")
public class AlgorithmStatsDto {

    @NotNull
    @Schema(
            description = "Unique algorithms",
            example = "[\"AES\"]",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String[] unique;

    @NotNull
    @Schema(
            description = "Key sizes",
            example = "[128]",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer[] keySizes;

    @Schema(
            description = "Total number of components",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer count;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("unique", StringUtils.join(unique))
        .append("keySizes", StringUtils.join(keySizes))
        .append("count", count)
        .toString();
    }
}
