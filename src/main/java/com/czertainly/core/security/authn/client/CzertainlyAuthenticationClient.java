package com.czertainly.core.security.authn.client;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.*;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.core.model.auth.AuthenticationRequestDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.client.dto.AuthenticationResponseDto;
import com.czertainly.core.security.authn.client.dto.UserDetailsDto;
import com.czertainly.core.security.exception.AuthenticationServiceException;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.OAuth2Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CzertainlyAuthenticationClient extends CzertainlyBaseAuthenticationClient {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ObjectMapper objectMapper;
    private final String customAuthServiceBaseUrl;

    @Value("${server.ssl.certificate-header-name}")
    private String certificateHeaderName;

    private final AuditLogService auditLogService;

    public CzertainlyAuthenticationClient(@Autowired AuditLogService auditLogService, @Autowired ObjectMapper objectMapper, @Value("${auth-service.base-url}") String customAuthServiceBaseUrl) {
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.customAuthServiceBaseUrl = customAuthServiceBaseUrl;
    }

    public AuthenticationInfo authenticate(HttpHeaders headers, boolean isLocalhostRequest, boolean allowTokenAuthentication) throws AuthenticationException {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    String.format(
                            "Calling authentication service with the following headers [%s].",
                            headers.entrySet().stream()
                                    .map(e -> String.format("%s='%s'", e.getKey(), String.join(", ", e.getValue())))
                                    .collect(Collectors.joining(","))
                    )
            );
        }

        AuthenticationRequestDto authRequest = getAuthPayload(headers, isLocalhostRequest, allowTokenAuthentication);

        try {

            WebClient.RequestHeadersSpec<?> request = getClient(customAuthServiceBaseUrl)
                    .post()
                    .uri("/auth")
                    .body(Mono.just(authRequest), AuthenticationRequestDto.class)
                    .accept(MediaType.APPLICATION_JSON);

            AuthenticationResponseDto response = request
                    .retrieve()
                    .bodyToMono(AuthenticationResponseDto.class)
                    .block();

            if (response == null) {
                String message = "Empty response received from authentication service";
                auditLogService.log(Module.AUTH, Resource.USER, Operation.AUTHENTICATION, OperationResult.FAILURE, message);
                throw new CzertainlyAuthenticationException(message);
            }
            return createAuthenticationInfo(authRequest.getAuthMethod(), response);
        } catch (WebClientResponseException.InternalServerError e) {
            String message = "An error occurred when calling authentication service";
            auditLogService.log(Module.AUTH, Resource.USER, Operation.AUTHENTICATION, OperationResult.FAILURE, message);
            throw new CzertainlyAuthenticationException(message, e);
        } catch (AuthenticationServiceException e) {
            auditLogService.log(Module.AUTH, Resource.USER, Operation.AUTHENTICATION, OperationResult.FAILURE, e.getException().getMessage());
            throw e;
        }
    }

    private AuthenticationRequestDto getAuthPayload(HttpHeaders headers, boolean isLocalhostRequest, boolean allowTokenAuthentication) {
        boolean hasAuthenticationMethod = false;
        AuthenticationRequestDto requestDto = new AuthenticationRequestDto();
        requestDto.setAuthMethod(AuthMethod.NONE);
        final List<String> certificateHeaderNameList = headers.get(certificateHeaderName);
        if (certificateHeaderNameList != null) {
            hasAuthenticationMethod = true;
            String certificateInHeader = URLDecoder.decode(certificateHeaderNameList.getFirst(), StandardCharsets.UTF_8);
            requestDto.setAuthMethod(AuthMethod.CERTIFICATE);
            requestDto.setCertificateContent(CertificateUtil.normalizeCertificateContent(certificateInHeader));
        }

        final List<String> authTokenHeaderNameList = headers.get(OAuth2Constants.TOKEN_AUTHENTICATION_HEADER);
        if (authTokenHeaderNameList != null && allowTokenAuthentication) {
            hasAuthenticationMethod = true;
            requestDto.setAuthenticationToken(authTokenHeaderNameList.getFirst());
            if (requestDto.getAuthMethod() == AuthMethod.NONE) {
                requestDto.setAuthMethod(AuthMethod.TOKEN);
            }
        }

        final List<String> systemUserHeaderNameList = headers.get(AuthHelper.SYSTEM_USER_HEADER_NAME);
        if (systemUserHeaderNameList != null) {
            hasAuthenticationMethod = true;
            requestDto.setSystemUsername(systemUserHeaderNameList.getFirst());
            if (requestDto.getAuthMethod() == AuthMethod.NONE) {
                requestDto.setAuthMethod(AuthMethod.USER_PROXY);
            }
        }

        final List<String> userUuidHeaderNameList = headers.get(AuthHelper.USER_UUID_HEADER_NAME);
        if (userUuidHeaderNameList != null) {
            hasAuthenticationMethod = true;
            requestDto.setUserUuid(userUuidHeaderNameList.getFirst());
            if (requestDto.getAuthMethod() == AuthMethod.NONE) {
                requestDto.setAuthMethod(AuthMethod.USER_PROXY);
            }
        }

        if (!hasAuthenticationMethod && isLocalhostRequest) {
            checkLocalhostUser(requestDto);
        }

        // update MDC for actor logging before authentication
        LoggingHelper.putActorInfoWhenNull(ActorType.USER, requestDto.getAuthMethod());

        return requestDto;
    }

    private void checkLocalhostUser(AuthenticationRequestDto requestDto) {
        AuthenticationSettingsDto authenticationSettings = SettingsCache.getSettings(SettingsSection.AUTHENTICATION);
        if (!authenticationSettings.isDisableLocalhostUser()) {
            requestDto.setSystemUsername(AuthHelper.LOCALHOST_USERNAME);
            if (requestDto.getAuthMethod() == AuthMethod.NONE) {
                requestDto.setAuthMethod(AuthMethod.USER_PROXY);
            }
        }
    }

    private AuthenticationInfo createAuthenticationInfo(AuthMethod authMethod, AuthenticationResponseDto response) {
        if (!response.isAuthenticated()) {
            AuthenticationInfo anonymousUserDetails = AuthenticationInfo.getAnonymousAuthenticationInfo();

            // update MDC for actor logging after successful authentication
            LoggingHelper.putActorInfoWhenNull(ActorType.ANONYMOUS, null, anonymousUserDetails.getUsername());

            return anonymousUserDetails;
        }

        try {
            UserDetailsDto userDetails = objectMapper.readValue(response.getData(), UserDetailsDto.class);

            // update MDC for actor logging after successful authentication
            LoggingHelper.putActorInfoWhenNull(ActorType.USER, userDetails.getUser().getUuid(), userDetails.getUser().getUsername());

            return new AuthenticationInfo(
                    authMethod,
                    userDetails.getUser().getUuid(),
                    userDetails.getUser().getUsername(),
                    userDetails.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toList()),
                    response.getData()
            );
        } catch (JsonProcessingException e) {
            throw new CzertainlyAuthenticationException("The response from the authentication service could not be parsed.", e);
        }
    }
}
