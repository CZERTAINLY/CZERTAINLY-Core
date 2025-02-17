package com.czertainly.core.auth.oauth2;

import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.OAuth2Constants;
import com.czertainly.core.util.OAuth2Util;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CzertainlyJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyJwtAuthenticationConverter.class);

    private CzertainlyAuthenticationClient authenticationClient;

    private AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Autowired
    public void setAuthenticationClient(CzertainlyAuthenticationClient authenticationClient) {
        this.authenticationClient = authenticationClient;
    }

    @Override
    public AbstractAuthenticationToken convert(@Nullable Jwt source) {
        if (source == null) {
            return (CzertainlyAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        }

        // Try to get additional information about user from User Info endpoint
        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        OAuth2ProviderSettingsDto providerSettings = authenticationSettings.getOAuth2Providers().values().stream().filter(p -> p.getIssuerUrl().equals(source.getIssuer().toString())).findFirst().orElse(null);
        if (providerSettings == null) {
            String message = "No OAuth2 Provider with issuer URI '%s' configured for authentication with JWT token".formatted(source.getIssuer());
            throw new CzertainlyAuthenticationException(message);
        }
        Map<String, Object> userInfoClaims = null;
        if (providerSettings.getUserInfoUrl() != null) {
            try {
                userInfoClaims = OAuth2Util.getUserInfo(providerSettings.getUserInfoUrl(), source.getTokenValue());
            } catch (Exception e) {
                logger.warn("Could not retrieve User Info for JWT: {}", e.getMessage());
            }
        }

        Map<String, Object> claims = OAuth2Util.mergeClaims(source.getClaims(), null, userInfoClaims);

        if (!claims.containsKey(OAuth2Constants.TOKEN_USERNAME_CLAIM_NAME)) {
            String message = "The username claim could not be retrieved from the Access Token or User Info Endpoint for user authenticating with Access Token %s".formatted(source.getTokenValue());
            auditLogService.logAuthentication(Operation.AUTHENTICATION, OperationResult.FAILURE, message, source.getTokenValue());
            throw new CzertainlyAuthenticationException(message);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();
        objectMapper.registerModule(module);

        HttpHeaders headers = new HttpHeaders();
        try {
            headers.add(OAuth2Constants.TOKEN_AUTHENTICATION_HEADER, objectMapper.writeValueAsString(claims));
        } catch (JsonProcessingException e) {
            String message = "Could not convert claims to JSON: %s".formatted(e.getMessage());
            auditLogService.logAuthentication(Operation.AUTHENTICATION, OperationResult.FAILURE, message, source.getTokenValue());
            throw new CzertainlyAuthenticationException(message);
        }
        AuthenticationInfo authInfo = authenticationClient.authenticate(headers, false);
        CzertainlyUserDetails userDetails = new CzertainlyUserDetails(authInfo);
        // Provider settings will not be null, otherwise converter would not have been reached from decoder
        logger.debug("User '{}' has been authenticated using JWT from OAuth2 Provider '{}'.", userDetails.getUsername(), providerSettings.getName());
        return new CzertainlyAuthenticationToken(userDetails);
    }
}
