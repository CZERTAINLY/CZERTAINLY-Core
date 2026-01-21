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
@Schema(description = "Cryptographic statistics")
public class CryptoStatsDto {

    @NotNull
    @Valid
    @Schema(
            description = "Cryptographic assets breakdown",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private CryptoAssetsDto assets;

    @NotNull
    @Valid
    @Schema(
            description = "Algorithm usage statistics",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private AlgorithmStatsDto algorithms;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("assets", assets)
                .append("algorithms", algorithms)
                .toString();
    }
}
