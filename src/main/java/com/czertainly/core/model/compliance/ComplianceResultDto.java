package com.czertainly.core.model.compliance;

import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class ComplianceResultDto implements Serializable {

    @Schema(description = "Overall compliance result status", requiredMode = Schema.RequiredMode.REQUIRED)
    private ComplianceStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    @Schema(description = "Date of the most recent compliance check", requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-09-11T13:45:30.123Z")
    private OffsetDateTime timestamp;

    @Schema(description = "Overall compliance check result message", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String message;

    @Schema(description = "List of internal rules", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private ComplianceResultRulesDto internalRules;

    @Schema(description = "List of groups", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<ComplianceResultProviderRulesDto> providerRules = new ArrayList<>();

}
