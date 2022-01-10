package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.v2.CertificateSignRequestDto;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeAuthorization;
import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jose.util.Base64URL;
import org.hibernate.type.StringNVarcharType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.DataInput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPublicKey;
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
    @Autowired
    private CertificateService certificateService;

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

        Map<String, Object> data = getJwsObject().getPayload().toJSONObject();
        AcmeOrder order = new AcmeOrder();
        order.setAcmeAccount(acmeAccount);
        order.setOrderId(AcmeRandomGeneratorAndValidator.generateRandomId());
        order.setStatus(OrderStatus.PENDING);
        order.setNotAfter((Date) data.get("notAfter"));
        order.setNotBefore((Date) data.get("notBefore"));
        List<Identifier> identifiers = getIdentifiers(data.get("identifiers").toString());
        order.setIdentifiers(AcmeSerializationUtil.serializeIdentifiers(identifiers));
        //TODO certificate, expires
        acmeOrderRepository.save(order);
        Set<AcmeAuthorization> authorizations = generateValidations(baseUrl, order, identifiers);
        order.setAuthorizations(authorizations);
        return order;
    }

    protected AcmeChallenge validateChallenge(String challengeId) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException {
        AcmeChallenge challenge = acmeChallengeRepository.findByChallengeId(challengeId).orElseThrow(() -> new NotFoundException(Challenge.class, challengeId));
        AcmeAuthorization authorization = challenge.getAuthorization();
        AcmeOrder order = authorization.getOrder();
        boolean isValid;
        if (challenge.getType().equals("http-01")) {
            isValid = validateHttpChallenge(challenge);
        } else {
            isValid = validateDnsChallenge(challenge);
        }
        if (isValid) {
            challenge.setValidated(new Date());
            challenge.setStatus(ChallengeStatus.VALID);
            authorization.setStatus(AuthorizationStatus.VALID);
            order.setStatus(OrderStatus.READY);
        } else {
            challenge.setStatus(ChallengeStatus.INVALID);
        }
        acmeChallengeRepository.save(challenge);
        acmeAuthorizationRepository.save(authorization);
        return challenge;
    }

    public AcmeOrder finalizeOrder(String orderId) throws JsonProcessingException, ConnectorException, CertificateException, AlreadyExistException {
        String csr = jwsObject.getPayload().toJSONObject().get("csr").toString();
        AcmeOrder order = acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException(Order.class, orderId));
        ClientCertificateSignRequestDto certificateSignRequestDto = new ClientCertificateSignRequestDto();
        certificateSignRequestDto.setAttributes(new ArrayList<>());
        certificateSignRequestDto.setPkcs10(csr);
        order.setStatus(OrderStatus.PROCESSING);
        acmeOrderRepository.save(order);
        try {
            ClientCertificateDataResponseDto certificateOutput = clientOperationService.issueCertificate(order.getAcmeAccount().getRaProfile().getUuid(), certificateSignRequestDto);
            order.setCertificateId(AcmeRandomGeneratorAndValidator.generateRandomId());
            order.setCertificateReference(certificateService.getCertificateEntity(certificateOutput.getUuid()));
            order.setStatus(OrderStatus.VALID);
            order.setExpires(new Date(new Date().getTime() + 10 * 60 * 60 * 1000));
        } catch (Exception e) {
            logger.error("Failed while issuing certificate. Exception is ");
            logger.error(e.getMessage());
            order.setStatus(OrderStatus.INVALID);
        }
        acmeOrderRepository.save(order);
        return order;
    }

    private Set<AcmeAuthorization> generateValidations(String baseUrl, AcmeOrder acmeOrder, List<Identifier> identifiers) {
        return Set.of(authorization(baseUrl, acmeOrder, identifiers));
    }

    private AcmeAuthorization authorization(String baseUrl, AcmeOrder acmeOrder, List<Identifier> identifiers) {
        AcmeAuthorization authorization = new AcmeAuthorization();
        authorization.setAuthorizationId(AcmeRandomGeneratorAndValidator.generateRandomId());
        authorization.setStatus(AuthorizationStatus.PENDING);
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
        challenge.setAuthorization(authorization);
        challenge.setType(challengeType);
        acmeChallengeRepository.save(challenge);
        return challenge;
    }

    private boolean checkWildcard(List<Identifier> identifiers) {
        return !identifiers.stream().filter(identifier -> identifier.getValue().contains("*")).collect(Collectors.toList()).isEmpty();
    }

    private List<Identifier> getIdentifiers(String identifierJson) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Identifier> identifiers = new ArrayList<>();
        try {
            for (LinkedHashMap<String, String> itr : (List<LinkedHashMap>) objectMapper.readValue(identifierJson, identifiers.getClass())) {
                identifiers.add(objectMapper.convertValue(itr, Identifier.class));
            }
        } catch (JsonProcessingException e) {
            logger.error("Unable to decide Identifiers. JSON parsing exceptions. Value of identifier is ", identifierJson);
        }
        return identifiers;
    }

    private String generateDnsValidationToken(String publicKey, String token) {
        MessageDigest digest;
        try {
            PublicKey pubKey = AcmePublicKeyProcessor.publicKeyObjectFromString(publicKey);
            digest = MessageDigest.getInstance("SHA-256");
            final byte[] encodedhashOfExpectedKeyAuthorization = digest.digest(AcmeCommonHelper.createKeyAuthorization(token, pubKey).getBytes(StandardCharsets.UTF_8));
            final String base64EncodedDigest = Base64URL.encode(encodedhashOfExpectedKeyAuthorization).toString();
            return base64EncodedDigest;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Error while generating Token for validations. SHA256 Algorithm is not supported");
            return null;
        }
    }



    private boolean validateHttpChallenge(AcmeChallenge challenge) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String response = getHttpChallengeResponse(
                AcmeSerializationUtil.deserializeIdentifier(
                        challenge
                                .getAuthorization()
                                .getIdentifier()
                )
                        .getValue(),
                challenge.getToken());
        PublicKey pubKey = AcmePublicKeyProcessor.publicKeyObjectFromString(challenge.getAuthorization().getOrder().getAcmeAccount().getPublicKey());
        String expectedResponse = AcmeCommonHelper.createKeyAuthorization(challenge.getToken(), pubKey);
        if(response.equals(expectedResponse)){
            return true;
        }
        return false;
    }

    private boolean validateDnsChallenge(AcmeChallenge challenge) {
        Properties env = new Properties();
        env.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.dns.DnsContextFactory");
        env.setProperty(Context.PROVIDER_URL, "dns://192.168.88.117");
        List<String> txtRecords = new ArrayList<>();
        String expectedKeyAuthorization = generateDnsValidationToken(challenge.getAuthorization().getOrder().getAcmeAccount().getPublicKey(), challenge.getToken());
        DirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attributes list = context.getAttributes("_acme-challenge.debian06.acme.local",
                    new String[]{"TXT"});
            NamingEnumeration<? extends Attribute> records = list.getAll();

            while (records.hasMore()) {
                Attribute record = records.next();
                txtRecords.add(record.get().toString());
            }
        } catch (NamingException e) {
            logger.error(e.getMessage());
        }
        if (txtRecords.isEmpty()) {
            logger.error("TXT record is empty");
            return false;
        }
        if (!txtRecords.contains(expectedKeyAuthorization)) {
            logger.error("TXT record not found");
            return false;
        }
        return true;
    }

    private String getHttpChallengeResponse(String domain, String token) {
        return getResponseFollowRedirects(String.format("http://%s/.well-known/acme-challenge/%s", domain, token));
    }

    private String getResponseFollowRedirects(String url) {
        String finalUrl = url;
        String acmeChallengeOutput = "";
        try {
            HttpURLConnection connection;
            do {
                connection = (HttpURLConnection) new URL(finalUrl).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestMethod("GET");
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    acmeChallengeOutput = bufferedReader.lines().collect(Collectors.joining());
                }
                if (responseCode >= 300 && responseCode < 400) {
                    String redirectedUrl = connection.getHeaderField("Location");
                    if (null == redirectedUrl) {
                        break;
                    }
                    finalUrl = redirectedUrl;
                } else
                    break;
            } while (connection.getResponseCode() != HttpURLConnection.HTTP_OK);
            connection.disconnect();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return acmeChallengeOutput;
    }
}
