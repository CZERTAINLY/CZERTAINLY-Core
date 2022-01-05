package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.v2.CertificateSignRequestDto;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeAuthorization;
import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AcmeJsonProcessor;
import com.czertainly.core.util.AcmeRandomGeneratorAndValidator;
import com.czertainly.core.util.AcmeSerializationUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.hibernate.type.StringNVarcharType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.transaction.Transactional;
import java.io.DataInput;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class provides helper service for extended methods and other implementations for acme
 * to be supported by AcmeService
 */
@Configuration
public class ExtendedAcmeHelperService {

    private AcmeJwsBody acmeJwsBody;
    private String rawJwsBody;
    private JWSObject jwsObject;
    private Boolean isValidSignature;
    private PublicKey publicKey;

    private static final Logger logger = LoggerFactory.getLogger(ExtendedAcmeHelperService.class);

    @Autowired
    private AcmeOrderRepository acmeOrderRepository;
    @Autowired
    private AcmeChallengeRepository acmeChallengeRepository;
    @Autowired
    private AcmeAuthorizationRepository acmeAuthorizationRepository;
    @Autowired
    private ClientOperationService clientOperationService;

    public ExtendedAcmeHelperService() {
    }

    public AcmeJwsBody getAcmeJwsBody() {
        return acmeJwsBody;
    }

    public String getRawJwsBody() {
        return rawJwsBody;
    }

    public JWSObject getJwsObject() {
        return jwsObject;
    }

    public Boolean getValidSignature() {
        return isValidSignature;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    private void setPublicKey() throws JOSEException {
        this.publicKey = ((RSAKey) jwsObject.getHeader().getJWK()).toPublicKey();
    }

    protected void setPublicKey(PublicKey publicKey) throws JOSEException {
        this.publicKey = publicKey;
    }

    private void setJwsObject() throws ParseException {
        JWSObject jwsObject = new JWSObject(new Base64URL(acmeJwsBody.getProtected()), new Base64URL(acmeJwsBody.getPayload()),
                new Base64URL(acmeJwsBody.getSignature()));
        this.jwsObject = jwsObject;
    }

    private void setIsValidSignature() throws JOSEException {
        this.isValidSignature = jwsObject.verify(new RSASSAVerifier((RSAPublicKey) publicKey));
    }

    protected Boolean IsValidSignature() throws JOSEException {
        this.isValidSignature = jwsObject.verify(new RSASSAVerifier((RSAPublicKey) publicKey));
        return this.isValidSignature;
    }

    protected void newAccountProcess() throws AcmeProblemDocumentException {
        try {
            this.setPublicKey();
            this.setIsValidSignature();
        } catch (Exception e) {
            ProblemDocument problemDocument = new ProblemDocument();
            problemDocument.setTitle("Invalid Request Body");
            problemDocument.setDetail("Request JWS is invalid and the server is unable to process");
            problemDocument.setType("urn:ietf:params:acme:error:malformed");
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, problemDocument);
        }
    }


    public void initialize(String rawJwsBody) throws AcmeProblemDocumentException {
        this.rawJwsBody = rawJwsBody;
        try {
            this.acmeJwsBody = AcmeJsonProcessor.generalBodyJsonParser(rawJwsBody, AcmeJwsBody.class);
            this.setJwsObject();
        } catch (Exception e) {
            ProblemDocument problemDocument = new ProblemDocument();
            problemDocument.setTitle("Invalid Request Body");
            problemDocument.setDetail("Request JWS is invalid and the server is unable to process");
            problemDocument.setType("urn:ietf:params:acme:error:malformed");
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, problemDocument);
        }
    }

    public AcmeOrder generateOrder(String baseUrl, AcmeAccount acmeAccount) {

        Map<String, Object> data =  getJwsObject().getPayload().toJSONObject();
        AcmeOrder order = new AcmeOrder();
        order.setAcmeAccount(acmeAccount);
        order.setOrderId(AcmeRandomGeneratorAndValidator.generateRandomId());
        order.setStatus(OrderStatus.PENDING);
        order.setNotAfter((Date) data.get("notAfter"));
        order.setNotBefore((Date) data.get("notBefore"));
        List<Identifier> identifiers = getIdentifiers(data.get("identifiers").toString());
        order.setIdentifiers(AcmeSerializationUtil.serializeIdentifiers(identifiers));
        order.setUrl(baseUrl + "/order/" + order.getOrderId());
        order.setFinalize(baseUrl + "/order/" + order.getOrderId() + "/finalize");
        //TODO certificate, expires
        acmeOrderRepository.save(order);
        Set<AcmeAuthorization> authorizations = generateValidations(baseUrl, order, identifiers);
        order.setAuthorizations(authorizations);
        return order;
    }

    protected AcmeChallenge validateChallenge(String challengeId) throws NotFoundException {
        AcmeChallenge challenge = acmeChallengeRepository.findByChallengeId(challengeId).orElseThrow(()-> new NotFoundException(Challenge.class, challengeId));
        AcmeAuthorization authorization = challenge.getAuthorization();
        generateValidationToken(authorization.getOrder().getAcmeAccount().getPublicKey(), challenge.getToken());
        boolean isValid;
        if(challenge.getType() == "http-01"){
            isValid = validateHttpChallenge(challenge);
        }else{
            isValid = validateDnsChallenge(challenge);
        }
        if(isValid){
            challenge.setValidated(new Date());
            challenge.setStatus(ChallengeStatus.VALID);
            authorization.setStatus(AuthorizationStatus.VALID);
        }else{
            challenge.setStatus(ChallengeStatus.INVALID);
        }
        acmeChallengeRepository.save(challenge);
        acmeAuthorizationRepository.save(authorization);
        return challenge;
    }

    public AcmeOrder finalizeOrder(String orderId) throws JsonProcessingException, NotFoundException {
        String csr = jwsObject.getPayload().toJSONObject().get("csr").toString();
        AcmeOrder order = acmeOrderRepository.findByOrderId(orderId).orElseThrow(()->new NotFoundException(Order.class, orderId));
        ClientCertificateSignRequestDto certificateSignRequestDto = new ClientCertificateSignRequestDto();
        certificateSignRequestDto.setAttributes(new ArrayList<>());
        certificateSignRequestDto.setPkcs10(csr);
        clientOperationService.issueCertificate(order.getAcmeAccount().getcertificateSignRequestDto);
        return order;
    }

    private Set<AcmeAuthorization> generateValidations(String baseUrl, AcmeOrder acmeOrder, List<Identifier> identifiers){
        return Set.of(authorization(baseUrl, acmeOrder, identifiers));
    }

    private AcmeAuthorization authorization(String baseUrl, AcmeOrder acmeOrder, List<Identifier> identifiers){
        AcmeAuthorization authorization = new AcmeAuthorization();
        authorization.setAuthorizationId(AcmeRandomGeneratorAndValidator.generateRandomId());
        authorization.setStatus(AuthorizationStatus.PENDING);
        authorization.setUrl(baseUrl + "/authz/" + authorization.getAuthorizationId());
        authorization.setOrder(acmeOrder);
        //TODO expires
        authorization.setWildcard(checkWildcard(identifiers));
        authorization.setIdentifier(AcmeSerializationUtil.serialize(identifiers.get(0)));
        acmeAuthorizationRepository.save(authorization);
        AcmeChallenge dnsChallenge = generateChallenge("dns-01", baseUrl, authorization);
        AcmeChallenge httpChallenge = generateChallenge("http-01", baseUrl, authorization);
        authorization.setChallenges(Set.of(dnsChallenge, httpChallenge));
        return authorization;
    }

    private AcmeChallenge generateChallenge(String challengeType, String baseUrl, AcmeAuthorization authorization) {
        AcmeChallenge challenge = new AcmeChallenge();
        challenge.setChallengeId(AcmeRandomGeneratorAndValidator.generateRandomId());
        challenge.setStatus(ChallengeStatus.PENDING);
        challenge.setToken(AcmeRandomGeneratorAndValidator.generateRandomTokenForValidation(publicKey));
        challenge.setUrl(baseUrl + "/chall/" + challenge.getChallengeId());
        challenge.setAuthorization(authorization);
        challenge.setType(challengeType);
        acmeChallengeRepository.save(challenge);
        return challenge;
    }

    private boolean checkWildcard(List<Identifier> identifiers) {
        return !identifiers.stream().filter(identifier -> identifier.getValue().contains("*")).collect(Collectors.toList()).isEmpty();
    }

    private List<Identifier> getIdentifiers(String identifierJson){
        ObjectMapper objectMapper = new ObjectMapper();
        List<Identifier> identifiers = new ArrayList<>();
        try {
            for(LinkedHashMap<String,String> itr: (List<LinkedHashMap>) objectMapper.readValue(identifierJson, identifiers.getClass())){
                identifiers.add(objectMapper.convertValue(itr, Identifier.class));
            }
        } catch (JsonProcessingException e) {
            logger.error("Unable to decide Identifiers. JSON parsing exceptions. Value of identifier is ", identifierJson);
        }
        return identifiers;
    }

    private String generateValidationToken(String publicKey, String token){
        byte[] publicBytes = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            byte[] result = digest.digest(pubKey.getEncoded());
            String thumbprint = Base64.getEncoder().encodeToString(result);
            return token + "." + thumbprint;
        }catch (NoSuchAlgorithmException | InvalidKeySpecException e){
            logger.error("Error while generating Token for validations. SHA256 Algorithm is not supported");
            return null;
        }
    }

    private boolean validateHttpChallenge(AcmeChallenge challenge){
        return true;
    }

    private boolean validateDnsChallenge(AcmeChallenge challenge){
        return true;
    }

}
