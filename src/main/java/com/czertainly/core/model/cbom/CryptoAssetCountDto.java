package com.czertainly.core.model.cbom;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CryptoAssetCountDto {

    @Schema(
            description = "Total number of crypto components",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer total;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("total", total)
        .toString();
    }
}
