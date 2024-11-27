package com.czertainly.core.auth.oauth2;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CzertainlyJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger logger = LoggerFactory.getLogger(CzertainlyJwtAuthenticationConverter.class);

    private CzertainlyAuthenticationClient authenticationClient;

    @Autowired
    public void setAuthenticationClient(CzertainlyAuthenticationClient authenticationClient) {
        this.authenticationClient = authenticationClient;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        if (source != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.add(Constants.TOKEN_AUTHENTICATION_HEADER, source.getTokenValue());
            AuthenticationInfo authInfo = authenticationClient.authenticate(headers, false, true);
            logger.debug("Authenticated user using JWT token.");
            return new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authInfo));
        } else return (CzertainlyAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    }
}
