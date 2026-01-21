package com.czertainly.core.model.cbom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Schema(description = "Problem Details per RFC 9457 (application/problem+json)")
public class ProblemDetailsDto {

    @Schema(
            description = "A URI reference that identifies the problem type. Defaults to 'about:blank'.",
            example = "https://example.com/problems/invalid-payload",
            format = "uri-reference"
    )
    private String type;

    @Schema(
            description = "Short, human-readable summary of the problem type.",
            example = "Invalid payload"
    )
    private String title;

    @Schema(
            description = "HTTP status code for this occurrence of the problem.",
            example = "400"
    )
    private Integer status;

    @Schema(
            description = "Human-readable explanation specific to this occurrence.",
            example = "The 'serialNumber' field is missing."
    )
    private String detail;

    @Schema(
            description = "A URI reference that identifies the specific occurrence of the problem.",
            example = "urn:uuid:4b96f3f7-0c2a-43f7-9c0a-7b0b6a3e2a61",
            format = "uri-reference"
    )
    private String instance;

    @Schema(description = "Additional properties for extensibility")
    private Map<String, Object> additionalProperties = new HashMap<>();

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("type", type)
                .append("title", title)
                .append("status", status)
                .append("detail", detail)
                .append("instance", instance)
                .append("additionalProperties", additionalProperties)
                .toString();
    }
}

