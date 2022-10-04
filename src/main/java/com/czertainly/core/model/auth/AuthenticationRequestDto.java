package com.czertainly.core.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AuthenticationRequestDto {

    @Schema(description = "Base64 Content of the certificate")
    private String certificateContent;

    @Schema(description = "Authentication Token")
    private String authenticationToken;

    @Schema(description = "System Username")
    private String systemUsername;

    public String getCertificateContent() {
        return certificateContent;
    }

    public void setCertificateContent(String certificateContent) {
        this.certificateContent = certificateContent;
    }

    public String getAuthenticationToken() {
        return authenticationToken;
    }

    public void setAuthenticationToken(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }

    public String getSystemUsername() {
        return systemUsername;
    }

    public void setSystemUsername(String systemUsername) {
        this.systemUsername = systemUsername;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("certificateContent", certificateContent)
                .append("authenticationToken", authenticationToken)
                .append("systemUsername", systemUsername)
                .toString();
    }
}
