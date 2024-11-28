package com.czertainly.core.security.authn.client;

import com.czertainly.api.model.core.logging.enums.AuthMethod;
import com.czertainly.api.model.core.settings.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.model.auth.AuthenticationRequestDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.client.dto.AuthenticationResponseDto;
import com.czertainly.core.security.authn.client.dto.UserDetailsDto;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

    protected final Log logger = LogFactory.getLog(this.getClass());

    private final ObjectMapper objectMapper;
    private final String customAuthServiceBaseUrl;

    @Value("${server.ssl.certificate-header-name}")
    private String certificateHeaderName;

    public CzertainlyAuthenticationClient(@Autowired ObjectMapper objectMapper, @Value("${auth-service.base-url}") String customAuthServiceBaseUrl) {
        this.objectMapper = objectMapper;
        this.customAuthServiceBaseUrl = customAuthServiceBaseUrl;
    }

    public AuthenticationInfo authenticate(HttpHeaders headers, boolean isLocalhostRequest, boolean allowTokenAuthentication) throws AuthenticationException {
        try {
            logger.trace(
                    String.format(
                            "Calling authentication service with the following headers [%s].",
                            headers.entrySet().stream()
                                    .map(e -> String.format("%s='%s'", e.getKey(), String.join(", ", e.getValue())))
                                    .collect(Collectors.joining(","))
                    )
            );

            AuthenticationRequestDto authRequest = getAuthPayload(headers, isLocalhostRequest, allowTokenAuthentication);
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
                throw new CzertainlyAuthenticationException("Empty response received from authentication service.");
            }
            return createAuthenticationInfo(authRequest.getAuthMethod(), response);
        } catch (WebClientResponseException.InternalServerError e) {
            throw new CzertainlyAuthenticationException("An error occurred when calling authentication service.", e);
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

        final List<String> authTokenHeaderNameList = headers.get(Constants.TOKEN_AUTHENTICATION_HEADER);
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
            return AuthenticationInfo.getAnonymousAuthenticationInfo();
        }

        try {
            UserDetailsDto userDetails = objectMapper.readValue(response.getData(), UserDetailsDto.class);
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
