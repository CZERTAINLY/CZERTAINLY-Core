package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.api.model.core.authority.RevocationReason;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.*;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.aspectj.weaver.ast.Not;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
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
    private static final String NONCE_HEADER_NAME = "Replay-Nonce";

    @Autowired
    private AcmeAccountRepository acmeAccountRepository;
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
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertValidationService certValidationService;
    @Autowired
    private AcmeProfileRepository acmeProfileRepository;

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
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }


    public void initialize(String rawJwsBody) throws AcmeProblemDocumentException {
        this.rawJwsBody = rawJwsBody;
        try {
            this.acmeJwsBody = AcmeJsonProcessor.generalBodyJsonParser(rawJwsBody, AcmeJwsBody.class);
            this.setJwsObject();
        } catch (Exception e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }

    public Directory frameDirectory(String profileName) throws NotFoundException {
        Directory directory = new Directory();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String replaceUrl;
        Boolean isRa;
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")){
            replaceUrl = "%s/acme/raProfile/%s/";
            isRa = true;
        }else{
            replaceUrl = "%s/acme/%s/";
            isRa = false;
        }
        directory.setNewNonce(String.format(replaceUrl + "new-nonce", baseUri, profileName));
        directory.setNewAccount(String.format(replaceUrl + "new-account", baseUri, profileName));
        directory.setNewOrder(String.format(replaceUrl + "new-order", baseUri, profileName));
        directory.setNewAuthz(String.format(replaceUrl + "new-authz", baseUri, profileName));
        directory.setRevokeCert(String.format(replaceUrl + "revoke-cert", baseUri, profileName));
        directory.setKeyChange(String.format(replaceUrl + "key-change", baseUri, profileName));
        directory.setMeta(frameDirectoryMeta(profileName, isRa));
        return directory;
    }

    private DirectoryMeta frameDirectoryMeta(String profileName, boolean isRa) throws NotFoundException {
        AcmeProfile acmeProfile;
        if(isRa){
            acmeProfile = getRaProfileEntity(profileName).getAcmeProfile();
        }else {
            acmeProfile = acmeProfileRepository.findByName(profileName);
        }
        DirectoryMeta meta = new DirectoryMeta();
//        meta.setCaaIdentities(Arrays.asList("example.com"));
        meta.setTermsOfService(acmeProfile.getTermsOfServiceUrl());
        meta.setExternalAccountRequired(false);
        meta.setWebsite(acmeProfile.getWebsite());
        return meta;
    }

    private RaProfile getRaProfileEntity(String name) throws NotFoundException {
        return raProfileRepository.findByName(name).orElseThrow(() -> new NotFoundException(RaProfile.class, name));
    }

    protected ResponseEntity<Account> processNewAccount(String profileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        newAccountValidator(profileName, requestJson);
        Account accountRequest = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), Account.class);
        AcmeAccount account = addNewAccount(profileName, AcmePublicKeyProcessor.publicKeyPemStringFromObject(getPublicKey()), accountRequest);
        Account accountDto = account.mapToDto();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")){
            accountDto.setOrders(String.format("%s/acme/raProfile/%s/acct/%s/orders", baseUri, profileName, account.getAccountId()));
            return ResponseEntity
                    .created(URI.create(String.format("%s/acme/raProfile/%s/acct/%s", baseUri, profileName, account.getAccountId())))
                    .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                    .body(accountDto);
        }
        else{
            accountDto.setOrders(String.format("%s/acme/%s/acct/%s/orders", baseUri, profileName, account.getAccountId()));
            return ResponseEntity
                    .created(URI.create(String.format("%s/acme/%s/acct/%s", baseUri, profileName, account.getAccountId())))
                    .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                    .body(accountDto);
        }

    }

    private void newAccountValidator(String profileName, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
        initialize(requestJson);
        newAccountProcess();
        //TODO Add main to validation
        //TODO Terms of Service validation
    }

    private AcmeAccount addNewAccount(String profileName, String publicKey, Account accountRequest) throws NotFoundException, AcmeProblemDocumentException {

        AcmeProfile acmeProfile = getAcmeProfileEntityByName(profileName);
        String accountId = AcmeRandomGeneratorAndValidator.generateRandomId();
        AcmeAccount oldAccount = acmeAccountRepository.findByPublicKey(publicKey);
        if(acmeProfile.getInsistContact() && accountRequest.getContact().isEmpty()){
            {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("invalidContact",
                        "Contact Information Not Found",
                        "Contact information is missing in the Request. It is set as mandatory for this profile"));
            }
        }

        if(acmeProfile.getInsistTermsOfService() && accountRequest.isTermsOfServiceAgreed()){
            {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("termsOfServiceNotAgreed",
                        "Terms of Service Not Agreed",
                        "Terms of Service is not agreed by the client. It is set as mandatory for this profile"));
            }
        }

        if(acmeProfile.getRaProfile() == null){
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("invalidRaProfile",
                    "RA Profile Not Associated",
                    "RA Profile is not associated for the selected ACME profile"));
        }
        if(oldAccount == null){
            if(accountRequest.isOnlyReturnExisting()){
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST);
            }
        }else{
            return oldAccount;
        }
        AcmeAccount account = new AcmeAccount();
        account.setAcmeProfile(acmeProfile);
        //TODO validate contact url for creating a new account
        account.setEnabled(false);
        account.setStatus(AccountStatus.VALID);
        account.setTermsOfServiceAgreed(true);
        if(acmeProfile.getRaProfile()!= null) {
            account.setRaProfile(acmeProfile.getRaProfile());
        }else{
            account.setRaProfile(raProfileRepository.findByAcmeProfile(acmeProfile));
        }
        account.setPublicKey(publicKey);
        account.setDefaultRaProfile(true);
        account.setAccountId(accountId);
        account.setContact(AcmeSerializationUtil.serialize(accountRequest.getContact()));
        acmeAccountRepository.save(account);
        return account;
    }

    protected ResponseEntity<Order> processNewOrder(String profileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        initialize(requestJson);
        String[] acmeAccountKeyIdSegment = getJwsObject().getHeader().getKeyID().split("/");
        String acmeAccountId = acmeAccountKeyIdSegment[acmeAccountKeyIdSegment.length - 1];
        AcmeAccount acmeAccount = getAcmeAccountEntity(acmeAccountId);
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String baseUrl;
        if(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")){
            baseUrl = String.format("%s/acme/raProfile/%s", baseUri, profileName);
        }else{
            baseUrl = String.format("%s/acme/%s", baseUri, profileName);
        }

        try {
            setPublicKey(AcmePublicKeyProcessor.publicKeyObjectFromString(acmeAccount.getPublicKey()));
            IsValidSignature();

            AcmeOrder order = generateOrder(baseUrl, acmeAccount);
            return ResponseEntity
                    .ok()
                    .location(URI.create(order.getUrl()))
                    .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                    .body(order.mapToDto());
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    protected AcmeOrder getAcmeOrderEntity(String orderId) throws NotFoundException {
        return acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException(Order.class, orderId));
    }

    private X509Certificate getX509(String certificate) throws CertificateException {
        return CertificateUtil.getX509Certificate(certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", ""));
    }

    protected String frameCertChainString(List<Certificate> certificates) throws CertificateException {
        List<String> chain = new ArrayList<>();
        for(Certificate certificate: certificates){
            chain.add(X509ObjectToString.toPem(getX509(certificate.getCertificateContent().getContent())));
        }
        return String.join("\r\n", chain);
    }

    protected ByteArrayResource getCertificateResource(String certificateId) throws NotFoundException, CertificateException {
        AcmeOrder order = acmeOrderRepository.findByCertificateId(certificateId).orElseThrow(() -> new NotFoundException(Order.class, certificateId));
        Certificate certificate = order.getCertificateReference();
        List<Certificate> chain = certValidationService.getCertificateChain(certificate);
        String chainString = frameCertChainString(chain);
        return new ByteArrayResource(chainString.getBytes(StandardCharsets.UTF_8));
    }

    public AcmeOrder generateOrder(String baseUrl, AcmeAccount acmeAccount) {

        Order orderRequest = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), Order.class);
        AcmeOrder order = new AcmeOrder();
        order.setAcmeAccount(acmeAccount);
        order.setOrderId(AcmeRandomGeneratorAndValidator.generateRandomId());
        order.setStatus(OrderStatus.PENDING);
        order.setNotAfter(AcmeCommonHelper.getDateFromString(orderRequest.getNotAfter()));
        order.setNotBefore(AcmeCommonHelper.getDateFromString(orderRequest.getNotBefore()));
        order.setIdentifiers(AcmeSerializationUtil.serializeIdentifiers(orderRequest.getIdentifiers()));
        order.setExpires(AcmeCommonHelper.getDefaultExpires());
        acmeOrderRepository.save(order);
        Set<AcmeAuthorization> authorizations = generateValidations(baseUrl, order, orderRequest.getIdentifiers());
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
        CertificateFinalizeRequest request = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), CertificateFinalizeRequest.class);
        AcmeOrder order = acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException(Order.class, orderId));
        ClientCertificateSignRequestDto certificateSignRequestDto = new ClientCertificateSignRequestDto();
        certificateSignRequestDto.setAttributes(new ArrayList<>());
        certificateSignRequestDto.setPkcs10(request.getCsr());
        order.setStatus(OrderStatus.PROCESSING);
        acmeOrderRepository.save(order);
        try {
            ClientCertificateDataResponseDto certificateOutput = clientOperationService.issueCertificate(order.getAcmeAccount().getRaProfile().getUuid(), certificateSignRequestDto);
            order.setCertificateId(AcmeRandomGeneratorAndValidator.generateRandomId());
            order.setCertificateReference(certificateService.getCertificateEntity(certificateOutput.getUuid()));
            order.setStatus(OrderStatus.VALID);
            order.setExpires(AcmeCommonHelper.getDefaultExpires());
        } catch (Exception e) {
            logger.error("Failed while issuing certificate. Exception is ");
            logger.error(e.getMessage());
            order.setStatus(OrderStatus.INVALID);
        }
        acmeOrderRepository.save(order);
        return order;
    }

    public ResponseEntity<List<Order>> listOrders(String accountId) throws NotFoundException {
        List<Order> orders = getAcmeAccountEntity(accountId)
                .getOrders()
                .stream()
                .map(AcmeOrder::mapToDto)
                .collect(Collectors.toList());
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(orders);
    }

    public Authorization checkDeactivateAuthorization(String authorizationId) throws NotFoundException {
        boolean isDeactivateRequest = false;
        if(getJwsObject().getPayload().toJSONObject() != null) {
            isDeactivateRequest = getJwsObject().getPayload().toJSONObject().getOrDefault("status", "") == "deactivated";
        }
        AcmeAuthorization authorization = acmeAuthorizationRepository.findByAuthorizationId(authorizationId).orElseThrow(() -> new NotFoundException(Authorization.class, authorizationId));

        if(isDeactivateRequest){
            authorization.setStatus(AuthorizationStatus.DEACTIVATED);
            acmeAuthorizationRepository.save(authorization);
        }
        return authorization.mapToDto();
    }

    public ResponseEntity<Account> updateAccount(String accountId) throws NotFoundException, AcmeProblemDocumentException {
        AcmeAccount account = getAcmeAccountEntity(accountId);
        Account request = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), Account.class);
        if(request.getContact() != null){
            account.setContact(AcmeSerializationUtil.serialize(request.getContact()));
        }
        if(request.getStatus().equals(AccountStatus.DEACTIVATED)){
            deactivateOrders(account.getOrders());
            account.setStatus(AccountStatus.DEACTIVATED);
        }
        acmeAccountRepository.save(account);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(account.mapToDto());
    }

    public ResponseEntity<?> revokeCertificate() throws ConnectorException, CertificateException {
        CertificateRevocationRequest request = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), CertificateRevocationRequest.class);
        X509Certificate x509Certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.getUrlDecoder().decode(request.getCertificate())));
        String decodedCertificate = X509ObjectToString.toPem(x509Certificate).replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", "");
        ClientCertificateRevocationDto revokeRequest = new ClientCertificateRevocationDto();
        Certificate cert = certificateService.getCertificateEntityByContent(decodedCertificate);

        revokeRequest.setReason(RevocationReason.fromCode(request.getReason().getCode()));
        revokeRequest.setAttributes(List.of());
        try {
            clientOperationService.revokeCertificate(cert.getRaProfile().getUuid(), cert.getUuid(), revokeRequest);
            return ResponseEntity
                    .ok()
                    .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                    .build();
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                    .build();
        }
    }

    public ResponseEntity<?> keyRollover() {
        JWSObject innerJws = getJwsObject().getPayload().toJWSObject();
        try {
            PublicKey newKey = ((RSAKey) innerJws.getHeader().getJWK()).toPublicKey();
            PublicKey oldKey = ((RSAKey) ((JWK)innerJws.getPayload().toJSONObject().get("oldKey"))).toPublicKey();
        } catch (JOSEException e) {
            e.printStackTrace();
        }
        String account = innerJws.getPayload().toJSONObject().get("account").toString();
        //TODO All key rollover checks
        return null;
    }

    private void deactivateOrders(Set<AcmeOrder> orders){
        for(AcmeOrder order: orders){
            order.setStatus(OrderStatus.INVALID);
            deactivateAuthorizations(order.getAuthorizations());
            acmeOrderRepository.save(order);
        }
    }

    private void deactivateAuthorizations(Set<AcmeAuthorization> authorizations){
        for(AcmeAuthorization authorization: authorizations){
            authorization.setStatus(AuthorizationStatus.DEACTIVATED);
            deactivateChallenges(authorization.getChallenges());
            acmeAuthorizationRepository.save(authorization);
        }
    }

    private void deactivateChallenges(Set<AcmeChallenge> challenges){
        for(AcmeChallenge challenge: challenges){
            challenge.setStatus(ChallengeStatus.INVALID);
            acmeChallengeRepository.save(challenge);
        }
    }

    private Set<AcmeAuthorization> generateValidations(String baseUrl, AcmeOrder acmeOrder, List<Identifier> identifiers) {
        return Set.of(authorization(baseUrl, acmeOrder, identifiers));
    }

    private AcmeAuthorization authorization(String baseUrl, AcmeOrder acmeOrder, List<Identifier> identifiers) {
        AcmeAuthorization authorization = new AcmeAuthorization();
        authorization.setAuthorizationId(AcmeRandomGeneratorAndValidator.generateRandomId());
        authorization.setStatus(AuthorizationStatus.PENDING);
        authorization.setOrder(acmeOrder);
        authorization.setExpires(AcmeCommonHelper.getDefaultExpires());
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

    private AcmeAccount getAcmeAccountEntity(String accountId) throws NotFoundException {
        return acmeAccountRepository.findByAccountId(accountId).orElseThrow(() -> new NotFoundException(Account.class, accountId));
    }

    private AcmeProfile getAcmeProfileEntityByName(String profileName) throws NotFoundException {
        if(acmeProfileRepository.existsByName(profileName)) {
            return acmeProfileRepository.findByName(profileName);
        }else{
            throw new NotFoundException(AcmeAccount.class, profileName);
        }
    }

    private boolean checkWildcard(List<Identifier> identifiers) {
        return !identifiers.stream().filter(identifier -> identifier.getValue().contains("*")).collect(Collectors.toList()).isEmpty();
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
        if (response.equals(expectedResponse)) {
            return true;
        }
        return false;
    }

    private boolean validateDnsChallenge(AcmeChallenge challenge) {
        Properties env = new Properties();
        env.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.dns.DnsContextFactory");
        AcmeProfile acmeProfile = challenge.getAuthorization().getOrder().getAcmeAccount().getAcmeProfile();
        if(acmeProfile.getDnsResolverIp() != null || !acmeProfile.getDnsResolverIp().isEmpty()){
            env.setProperty(Context.PROVIDER_URL, "dns://");
        }else {
            env.setProperty(Context.PROVIDER_URL, "dns://" + acmeProfile.getDnsResolverIp() + ":" + Optional.ofNullable(acmeProfile.getDnsResolverPort())
                    .orElse("53")).toString();
        }
        List<String> txtRecords = new ArrayList<>();
        String expectedKeyAuthorization = generateDnsValidationToken(challenge.getAuthorization().getOrder().getAcmeAccount().getPublicKey(), challenge.getToken());
        DirContext context;
        try {
            context = new InitialDirContext(env);
            Attributes list = context.getAttributes("_acme-challenge."
                            + AcmeSerializationUtil.deserializeIdentifier(
                                    challenge.getAuthorization().getIdentifier()).getValue(),
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
