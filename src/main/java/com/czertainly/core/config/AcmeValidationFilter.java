package com.czertainly.core.config;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.model.common.JwsBody;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.api.model.core.acme.Problem;
import com.czertainly.api.model.core.acme.ProblemDocument;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.*;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.acme.impl.ExtendedAcmeHelperService;
import com.czertainly.core.util.AcmeJsonProcessor;
import com.czertainly.core.util.AcmePublicKeyProcessor;
import com.czertainly.core.util.SerializationUtil;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


public class AcmeValidationFilter extends OncePerRequestFilter {

    @Autowired
    private AcmeAccountRepository acmeAccountRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AcmeProfileRepository acmeProfileRepository;
    @Autowired
    private AcmeOrderRepository acmeOrderRepository;
    @Autowired
    private AcmeAuthorizationRepository acmeAuthorizationRepository;
    @Autowired
    private AcmeChallengeRepository acmeChallengeRepository;
    @Autowired
    private ExtendedAcmeHelperService extendedAcmeHelperService;
    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String requestUrl = request.getRequestURL().toString();
        Boolean raProfileBased;
        if (!requestUri.startsWith("/api/acme/")) {
            filterChain.doFilter(request, response);
            return;
        }
        logger.info("ACME Request from " + request.getRemoteAddr() + " for " + requestUri);
        CustomHttpServletRequestWrapper requestWrapper = new CustomHttpServletRequestWrapper(request);
        try {
            if (requestUri.contains("/raProfile/")) {
                raProfileBased = true;
            } else {
                raProfileBased = false;
            }
            filterChain.doFilter(requestWrapper, response);
            Map pathVariables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            validate(requestUrl, requestUri, raProfileBased, pathVariables, requestWrapper);
        } catch (AcmeProblemDocumentException e) {
            resolver.resolveException(request, response, null, e);
        }
    }

    private void validate(String requestUrl, String requestUri, Boolean raProfileBased, Map pathVariables,
                          CustomHttpServletRequestWrapper requestWrapper) throws AcmeProblemDocumentException {
        validateGeneral(requestUrl, requestUri, requestWrapper);
        if (raProfileBased) {
            validateRaBasedAcme(pathVariables);
        } else {
            validateAcme(pathVariables);
        }
        validateAccount(requestUri, pathVariables);
        validateExpires(requestUri, pathVariables);
    }

    private void validateExpires(String requestUri, Map pathVariables) throws AcmeProblemDocumentException {
        if(requestUri.contains("/order/")){
            String orderId = (String) pathVariables.getOrDefault("orderId", "");
            AcmeOrder order = acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new
                    AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("orderNotFound",
                    "Order Not Found",
                    "Requested order is not found")));
            if(order.getExpires() != null){
                if(order.getExpires().before(new Date())){
                    throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                            new ProblemDocument("orderExpired",
                                    "Order Expired",
                                    "Expiry of the order is reached"));
                }
            }
        }

        if(requestUri.contains("/authz/")){
            String authorizationId = (String) pathVariables.getOrDefault("authorizationId", "");
            AcmeAuthorization authorization = acmeAuthorizationRepository.findByAuthorizationId(authorizationId)
                    .orElseThrow(() -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                            Problem.ACCOUNT_DOES_NOT_EXIST));
            if(authorization.getExpires() != null){
                if(authorization.getExpires().before(new Date())){
                    throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                            new ProblemDocument("authNotFound",
                                    "Authorization Expired",
                                    "Expiry of the authorization is reached"));
                }
            }
        }

        if(requestUri.contains("/chall/")){
            String challengeId = (String) pathVariables.getOrDefault("challengeId", "");
            AcmeChallenge challenge = acmeChallengeRepository.findByChallengeId(challengeId)
                    .orElseThrow(() -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                            Problem.ACCOUNT_DOES_NOT_EXIST));
            if(challenge.getAuthorization().getExpires() != null){
                if(challenge.getAuthorization().getExpires().before(new Date())){
                    throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                            new ProblemDocument("challengeNotFound",
                                    "Challenge Expired",
                                    "Expiry of the challenge is reached"));
                }
            }
        }
    }

    private void validateAccount(String requestUri, Map pathVariable) throws AcmeProblemDocumentException {
        if (!requestUri.contains("/acct/")) {
            return;
        }
        String accountId = (String) pathVariable.getOrDefault("accountId", "");
        AcmeAccount acmeAccount = acmeAccountRepository.findByAccountId(accountId)
                .orElseThrow(() ->
                        new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST));
        if (!acmeAccount.getStatus().equals(AccountStatus.VALID)) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("accountDeactivated",
                    "Account Deactivated",
                    "The requested account has been deactivated"));
        }
    }

    private void validateGeneral(String requestUrl, String requestUri, CustomHttpServletRequestWrapper requestWrapper) throws AcmeProblemDocumentException {
        validateJwsHeader(requestUrl, requestUri, requestWrapper);
    }

    private void validateAcme(Map pathVariables) throws AcmeProblemDocumentException {
        String acmeProfileName = (String) pathVariables.getOrDefault("acmeProfileName", "");
        AcmeProfile acmeProfile = acmeProfileRepository.findByName(acmeProfileName);
        if (acmeProfile == null) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileNotFound",
                            "ACME Profile is not found",
                            "Given ACME Profile in the request URL is not found"));
        }

        if (!acmeProfile.isEnabled()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileDisabled",
                            "ACME Profile is not enabled",
                            "ACME Profile is not enabled"));
        }
        if (acmeProfile.getRaProfile() == null) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("raProfileNotFound",
                            "RA Profile is not found",
                            "RA Profile is not found"));
        }
        if (!acmeProfile.getRaProfile().getEnabled()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("raProfileDisabled",
                            "RA Profile is not enabled",
                            "RA Profile is not enabled"));
        }
        if(acmeProfile.isDisableNewOrders()){
            ProblemDocument problemDocument = new ProblemDocument(Problem.USER_ACTION_REQUIRED);
            problemDocument.setInstance(acmeProfile.getTermsOfServiceUrl());
            problemDocument.setDetail("Terms of service have changed");
            Map<String, String> additionalHeaders = new HashMap<>();
            additionalHeaders.put("Link", "<" + acmeProfile.getTermsOfServiceChangeUrl() +">;rel=\"terms-of-service\"");
            throw new AcmeProblemDocumentException(HttpStatus.FORBIDDEN, problemDocument, additionalHeaders);
        }
    }

    private void validateRaBasedAcme(Map pathVariables) throws AcmeProblemDocumentException {
        String raProfileName = (String) pathVariables.getOrDefault("raProfileName","");
        RaProfile raProfile = raProfileRepository.findByName(raProfileName).orElseThrow(() ->
                new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                        new ProblemDocument("raProfileNotFound",
                                "RA Profile is not found",
                                "Given RA Profile in the request URL is not found")));
        if (raProfile.getAcmeProfile() == null) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileNotAssociated",
                            "ACME Profile is not associated",
                            "ACME Profile is not associated with the RA Profile"));
        }
        if (!raProfile.getEnabled()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("raProfileDisabled",
                            "RA Profile is not enabled",
                            "RA Profile is not enabled"));
        }

        if (!raProfile.getAcmeProfile().isEnabled()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileDisabled",
                            "ACME Profile is not enabled",
                            "ACME Profile is not enabled"));
        }

    }

    private void validateJwsHeader(String requestUrl, String requestUri, CustomHttpServletRequestWrapper requestWrapper) throws AcmeProblemDocumentException {
        if (requestUri.endsWith("/new-nonce") || requestUri.endsWith("/directory") || !requestUri.contains("/api/acme/")) {
            return;
        }
        String requestBody = "";
        JwsBody acmeData;
        JWSObject jwsObject;
        try {
            requestBody = requestWrapper.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            logger.error("Unable to parse request body", e);
        }
        try {
            acmeData = AcmeJsonProcessor.generalBodyJsonParser(requestBody, JwsBody.class);
            jwsObject = new JWSObject(new Base64URL(acmeData.getProtected()), new Base64URL(acmeData.getPayload()),
                    new Base64URL(acmeData.getSignature()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error("Unable to parse JWS object");
            throw new AcmeProblemDocumentException(
                    HttpStatus.BAD_REQUEST, Problem.MALFORMED
            );
        }
        //Validate JWS Header for Nonce if it has the correct value
        Map<String, Object> jwsHeader = jwsObject.getHeader().toJSONObject();
        validateNonce(jwsHeader.get("nonce"));
        validateUrl(jwsObject.getHeader().toJSONObject().get("url").toString(), requestUrl);
        validateKid(jwsObject, requestUri);
    }

    private void validateNonce(Object nonce) throws AcmeProblemDocumentException {
        if (nonce == null) {
            logger.error("Nonce is not found in the request");
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_NONCE);
        }
        extendedAcmeHelperService.isNonceValid(nonce.toString());
    }

    private void validateUrl(String url, String requestUrl) throws AcmeProblemDocumentException {
        if(!requestUrl.equals(url)){
            logger.error("Request URL: " + requestUrl + " does not match the JWS Header URL: " + url);
            throw new AcmeProblemDocumentException(HttpStatus.UNAUTHORIZED, Problem.MALFORMED, "Request URL and the header URL does not match");
        }
    }

    private void validateKid(JWSObject jwsObject, String requestUri) throws AcmeProblemDocumentException {
        Map<String, Object> jwsHeader = jwsObject.getHeader().toJSONObject();
        if(jwsHeader.containsKey("kid") && jwsHeader.containsKey("jwk")){
            logger.error("JWK Header contains both kid and jwk");
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        if(!jwsHeader.containsKey("kid") && !jwsHeader.containsKey("jwk")){
            logger.error("JWK Header does not contains both kid and jwk");
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        if(requestUri.contains("/new-account")){
            if(!jwsHeader.containsKey("jwk")){
                logger.error("New Account and Revocation of Certificate should have JWK in header");
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
            }
            try {
                PublicKey publicKey = ((RSAKey) jwsObject.getHeader().getJWK()).toPublicKey();
                validateSignature(publicKey, jwsObject, requestUri.contains("/revoke-cert"));
            } catch (JOSEException e) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }

        }else{
            if(!jwsHeader.containsKey("kid")){
                logger.error("Request should contain account url in kid of header");
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
            }
            AcmeAccount account = acmeAccountRepository.findByAccountId(
                            jwsHeader.get("kid").toString().split("/")[jwsHeader.get("kid").toString().split("/").length -1])
                    .orElseThrow(
                            () -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST));
            PublicKey publicKey = null;
            try {
                publicKey = AcmePublicKeyProcessor.publicKeyObjectFromString(account.getPublicKey());
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }
            validateSignature(publicKey, jwsObject, false);
        }
    }

    public void validateSignature(PublicKey publicKey, JWSObject jwsObject, Boolean isCertRevoke) throws AcmeProblemDocumentException {
        if(! isCertRevoke) {
            try {
                if (jwsObject.verify(new RSASSAVerifier((RSAPublicKey) publicKey))) {
                    return;
                }
            } catch (JOSEException e) {
                logger.error("Unable to verify signature: {}", e);
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }
            logger.error("Unable to verify the signature");
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
        }
        // For revocation handle the verification inside the service
    }
}
