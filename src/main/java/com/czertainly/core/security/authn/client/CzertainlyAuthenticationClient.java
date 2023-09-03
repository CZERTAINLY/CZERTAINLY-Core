package com.czertainly.core.security.authn.client;

import com.czertainly.core.model.auth.AuthenticationRequestDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.security.authn.client.dto.AuthenticationResponseDto;
import com.czertainly.core.security.authn.client.dto.UserDetailsDto;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.CertificateUtil;
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

import java.io.UnsupportedEncodingException;
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

    @Value("${auth.token.header-name}")
    private String authTokenHeaderName;

    public CzertainlyAuthenticationClient(@Autowired ObjectMapper objectMapper, @Value("${auth-service.base-url}") String customAuthServiceBaseUrl) {

        this.objectMapper = objectMapper;
        this.customAuthServiceBaseUrl = customAuthServiceBaseUrl;
    }

    public AuthenticationInfo authenticate(HttpHeaders headers) throws AuthenticationException {
        try {
            logger.trace(
                    String.format(
                            "Calling authentication service with the following headers [%s].",
                            headers.entrySet().stream()
                                    .map(e -> String.format("%s='%s'", e.getKey(), String.join(", ", e.getValue())))
                                    .collect(Collectors.joining(","))
                    )
            );
            WebClient.RequestHeadersSpec<?> request = getClient(customAuthServiceBaseUrl)
                    .post()
                    .uri("/auth")
                    .body(Mono.just(getAuthPayload(headers)), AuthenticationRequestDto.class)
                    .accept(MediaType.APPLICATION_JSON);

            AuthenticationResponseDto response = request
                    .retrieve()
                    .bodyToMono(AuthenticationResponseDto.class)
                    .block();

            if (response == null) {
                throw new CzertainlyAuthenticationException("Empty response received from authentication service.");
            }
            return createAuthenticationInfo(response);
        } catch (WebClientResponseException.InternalServerError e) {
            throw new CzertainlyAuthenticationException("An error occurred when calling authentication service.", e);
        }
    }

    private AuthenticationRequestDto getAuthPayload(HttpHeaders headers) {
        AuthenticationRequestDto requestDto = new AuthenticationRequestDto();
        final List<String> certificateHeaderNameList = headers.get(certificateHeaderName);
        if( certificateHeaderNameList != null) {
            try {
                String certificateInHeader = java.net.URLDecoder.decode(certificateHeaderNameList.get(0), StandardCharsets.UTF_8.name());
                requestDto.setCertificateContent(CertificateUtil.normalizeCertificateContent(certificateInHeader));
            } catch (UnsupportedEncodingException e) {
                logger.debug("Header not URL encoded");
                requestDto.setCertificateContent(certificateHeaderNameList.get(0));
            }
        }

        final List<String> authTokenHeaderNameList = headers.get(authTokenHeaderName);
        if(authTokenHeaderNameList != null) {
            requestDto.setAuthenticationToken(authTokenHeaderNameList.get(0));
        }

        final List<String> systemUserHeaderNameList = headers.get(AuthHelper.SYSTEM_USER_HEADER_NAME);
        if(systemUserHeaderNameList != null){
            requestDto.setSystemUsername(systemUserHeaderNameList.get(0));
        }

        final List<String> userUuidHeaderNameList = headers.get(AuthHelper.USER_UUID_HEADER_NAME);
        if(userUuidHeaderNameList != null){
            requestDto.setUserUuid(userUuidHeaderNameList.get(0));
        }
        return requestDto;
    }

    private AuthenticationInfo createAuthenticationInfo(AuthenticationResponseDto response) {
        if (!response.isAuthenticated()) {
            return AuthenticationInfo.getAnonymousAuthenticationInfo();
        }

        try {
            UserDetailsDto userDetails = objectMapper.readValue(response.getData(), UserDetailsDto.class);
            return new AuthenticationInfo(
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
