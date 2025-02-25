package com.czertainly.core.security.oauth2;

import com.czertainly.core.util.OAuth2Constants;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.PrivateKey;
import java.util.Date;

public class OAuth2TestUtil {

    public static String createJwtTokenValue(PrivateKey privateKey, Integer expiryInMilliseconds, String issuerUrl, String audience, String username) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .audience(audience)
                .expirationTime(expiryInMilliseconds == null ? null : new Date(System.currentTimeMillis() + expiryInMilliseconds))
                .claim(OAuth2Constants.TOKEN_USERNAME_CLAIM_NAME, username)
                .issuer(issuerUrl)
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        JWSSigner signer = new RSASSASigner(privateKey);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }
}
