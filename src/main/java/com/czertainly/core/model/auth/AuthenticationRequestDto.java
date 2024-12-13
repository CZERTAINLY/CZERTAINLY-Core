package com.czertainly.core.model.auth;

import com.czertainly.api.model.core.logging.enums.AuthMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Setter
@Getter
public class AuthenticationRequestDto {

    @JsonIgnore
    private AuthMethod authMethod;

    @Schema(description = "Base64 Content of the certificate")
    private String certificateContent;

    @Schema(description = "Authentication Token")
    private String authenticationToken;

    @Schema(description = "System Username")
    private String systemUsername;

    @Schema(description = "User UUID")
    private String userUuid;

    @JsonIgnore
    public String getAuthData() {
        return switch (authMethod) {
            case NONE -> null;
            case CERTIFICATE -> certificateContent;
            case TOKEN -> authenticationToken;
            case SESSION -> authenticationToken;
            case API_KEY -> null;
            case USER_PROXY -> systemUsername != null ? systemUsername : userUuid;
        };
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("certificateContent", certificateContent)
                .append("authenticationToken", authenticationToken)
                .append("systemUsername", systemUsername)
                .append("userUuid", userUuid)
                .toString();
    }
}
