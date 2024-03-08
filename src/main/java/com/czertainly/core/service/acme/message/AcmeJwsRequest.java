package com.czertainly.core.service.acme.message;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.model.common.JwsBody;
import com.czertainly.api.model.core.acme.Problem;
import com.czertainly.core.service.acme.AcmeConstants;
import com.czertainly.core.util.AcmeJsonProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.util.Base64URL;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;

public class AcmeJwsRequest {

    private static final Logger logger = LoggerFactory.getLogger(AcmeJwsRequest.class);

    private final String request;
    @Getter
    private JWSObject jwsObject;
    @Getter
    private PublicKey publicKey;
    private String keyType;

    public AcmeJwsRequest(String request) throws AcmeProblemDocumentException {
        this.request = request;
        readRequest();
        //readPublicKey();
        //checkSignature();
    }

    public boolean checkSignature(PublicKey publicKey) throws AcmeProblemDocumentException {
        try {
            if (keyType.equals(AcmeConstants.RSA_KEY_TYPE_NOTATION)) {
                return jwsObject.verify(new RSASSAVerifier((RSAPublicKey) publicKey));
            } else if (keyType.equals(AcmeConstants.EC_KEY_TYPE_NOTATION)) {
                return jwsObject.verify(new ECDSAVerifier((ECPublicKey) publicKey));
            } else {
                String message = "Account key is generated using unsupported key type by the server. Supported key types are " + String.join(", ", AcmeConstants.ACME_SUPPORTED_ALGORITHMS);
                logger.error(message);
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY, message);
            }
        } catch (JOSEException e) {
            logger.error("Error while verifying JWS signature, {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }

    public void readRequest() throws AcmeProblemDocumentException {
        try{
            JwsBody jwsBody = AcmeJsonProcessor.generalBodyJsonParser(request, JwsBody.class);
            this.jwsObject = new JWSObject(new Base64URL(jwsBody.getProtected()), new Base64URL(jwsBody.getPayload()),
                    new Base64URL(jwsBody.getSignature()));
        } catch (ParseException | JsonProcessingException e) {
            logger.error("Error while parsing JWS request, {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }

    public void readPublicKey() throws AcmeProblemDocumentException {
        this.keyType = jwsObject.getHeader().getJWK().getKeyType().toString();
        logger.debug("JWS public key type: {}", keyType);
        try {
            if (keyType.equals(AcmeConstants.RSA_KEY_TYPE_NOTATION)) {
                this.publicKey = jwsObject.getHeader().getJWK().toRSAKey().toPublicKey();
            } else if (keyType.equals(AcmeConstants.EC_KEY_TYPE_NOTATION)) {
                this.publicKey = jwsObject.getHeader().getJWK().toECKey().toPublicKey();
            } else {
                String message = "Account key is generated using unsupported key type by the server. Supported key types are " + String.join(", ", AcmeConstants.ACME_SUPPORTED_ALGORITHMS);
                logger.error(message);
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY, message);
            }
        } catch (JOSEException e) {
            logger.error("Error while parsing JWS public key, {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }

    public void checkSignature() throws AcmeProblemDocumentException {
        try {
            if (keyType.equals(AcmeConstants.RSA_KEY_TYPE_NOTATION)) {
                jwsObject.verify(new RSASSAVerifier((RSAPublicKey) publicKey));
            } else if (keyType.equals(AcmeConstants.EC_KEY_TYPE_NOTATION)) {
                jwsObject.verify(new ECDSAVerifier((ECPublicKey) publicKey));
            } else {
                String message = "Account key is generated using unsupported key type by the server. Supported key types are " + String.join(", ", AcmeConstants.ACME_SUPPORTED_ALGORITHMS);
                logger.error(message);
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY, message);
            }
        } catch (JOSEException e) {
            logger.error("Error while verifying JWS signature, {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }

}
