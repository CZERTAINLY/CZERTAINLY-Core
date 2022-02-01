package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.JwsBody;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.api.model.core.authority.RevocationReason;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.*;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.*;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.*;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

@Configuration
public class ExtendedAcmeHelperService {

    private JwsBody acmeJwsBody;
    private String rawJwsBody;
    private JWSObject jwsObject;
    private Boolean isValidSignature;
    private PublicKey publicKey;

    private static final Logger logger = LoggerFactory.getLogger(ExtendedAcmeHelperService.class);
    private static final String NONCE_HEADER_NAME = "Replay-Nonce";
    private static final String RETRY_HEADER_NAME = "Retry-After";
    private static final Integer NONCE_VALIDITY = 1 * 60 * 60; //1 Hour
    private static final Integer MAX_REDIRECT_COUNT = 15;
    private static final String CERTIFICATE_TYPE = "X.509";
    private static final String MESSAGE_DIGEST_ALGORITHM = "SHA-256";
    private static final String DNS_RECORD_TYPE = "TXT";
    private static final String DNS_ACME_PREFIX = "_acme-challenge.";
    private static final String DEFAULT_DNS_PORT = "53";
    private static final String DNS_CONTENT_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final String DNS_ENV_PREFIX = "dns://";
    private static final String HTTP_CHALLENGE_REQUEST_METHOD = "GET";
    private static final String LOCATION_HEADER_NAME = "Location";


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
    @Autowired
    private AcmeNonceRepository acmeNonceRepository;

    public ExtendedAcmeHelperService() {
    }

    public JwsBody getAcmeJwsBody() {
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
            logger.error("Error while parsing the JWS. JWS may be malformed: {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }


    public void initialize(String rawJwsBody) throws AcmeProblemDocumentException {
        this.rawJwsBody = rawJwsBody;
        try {
            this.acmeJwsBody = AcmeJsonProcessor.generalBodyJsonParser(rawJwsBody, JwsBody.class);
            this.setJwsObject();
        } catch (Exception e) {
            logger.error("Error while parsing JWS, {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
    }

    public Directory frameDirectory(String profileName) throws AcmeProblemDocumentException {
        logger.debug("Framing the directory for the profile with name: {}", profileName);
        Directory directory = new Directory();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String replaceUrl;
        Boolean isRa;
        if (ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")) {
            replaceUrl = "%s/acme/raProfile/%s/";
            isRa = true;
        } else {
            replaceUrl = "%s/acme/%s/";
            isRa = false;
        }
        directory.setNewNonce(String.format(replaceUrl + "new-nonce", baseUri, profileName));
        directory.setNewAccount(String.format(replaceUrl + "new-account", baseUri, profileName));
        directory.setNewOrder(String.format(replaceUrl + "new-order", baseUri, profileName));
        directory.setNewAuthz(String.format(replaceUrl + "new-authz", baseUri, profileName));
        directory.setRevokeCert(String.format(replaceUrl + "revoke-cert", baseUri, profileName));
        directory.setKeyChange(String.format(replaceUrl + "key-change", baseUri, profileName));
        try {
            directory.setMeta(frameDirectoryMeta(profileName, isRa));
        } catch (NotFoundException e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("profileNotFound", "Profile Not Found", "Given profile name is not found"));
        }
        logger.debug("Directory framed: {}", directory);
        return directory;
    }

    private DirectoryMeta frameDirectoryMeta(String profileName, boolean isRa) throws NotFoundException {
        AcmeProfile acmeProfile;
        if (isRa) {
            acmeProfile = getRaProfileEntity(profileName).getAcmeProfile();
        } else {
            acmeProfile = acmeProfileRepository.findByName(profileName);
        }
        DirectoryMeta meta = new DirectoryMeta();
        meta.setCaaIdentities(new String[0]);
        meta.setTermsOfService(acmeProfile.getTermsOfServiceUrl());
        meta.setExternalAccountRequired(false);
        meta.setWebsite(acmeProfile.getWebsite());
        logger.debug("Directory meta: {}", meta);
        return meta;
    }

    private RaProfile getRaProfileEntity(String name) throws NotFoundException {
        return raProfileRepository.findByName(name).orElseThrow(() -> new NotFoundException(RaProfile.class, name));
    }

    protected ResponseEntity<Account> processNewAccount(String profileName, String requestJson) throws AcmeProblemDocumentException {
        newAccountValidator(profileName, requestJson);
        Account accountRequest = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), Account.class);
        logger.debug("New Account requested: {}", accountRequest.toString());
        AcmeAccount account;
        account = addNewAccount(profileName, AcmePublicKeyProcessor.publicKeyPemStringFromObject(getPublicKey()), accountRequest);
        Account accountDto = account.mapToDto();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        if (ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")) {
            accountDto.setOrders(String.format("%s/acme/raProfile/%s/acct/%s/orders", baseUri, profileName, account.getAccountId()));
            if(accountRequest.isOnlyReturnExisting()){
                return ResponseEntity
                        .ok()
                        .location(URI.create(String.format("%s/acme/raProfile/%s/acct/%s", baseUri, profileName, account.getAccountId())))
                        .header(NONCE_HEADER_NAME, generateNonce())
                        .header(RETRY_HEADER_NAME, account.getAcmeProfile().getRetryInterval().toString())
                        .body(accountDto);
            }
            return ResponseEntity
                    .created(URI.create(String.format("%s/acme/raProfile/%s/acct/%s", baseUri, profileName, account.getAccountId())))
                    .header(NONCE_HEADER_NAME, generateNonce())
                    .header(RETRY_HEADER_NAME, account.getAcmeProfile().getRetryInterval().toString())
                    .body(accountDto);
        } else {
            accountDto.setOrders(String.format("%s/acme/%s/acct/%s/orders", baseUri, profileName, account.getAccountId()));
            if(accountRequest.isOnlyReturnExisting()){
                return ResponseEntity
                        .ok()
                        .location(URI.create(String.format("%s/acme/%s/acct/%s", baseUri, profileName, account.getAccountId())))
                        .header(NONCE_HEADER_NAME, generateNonce())
                        .header(RETRY_HEADER_NAME, account.getAcmeProfile().getRetryInterval().toString())
                        .body(accountDto);
            }
            return ResponseEntity
                    .created(URI.create(String.format("%s/acme/%s/acct/%s", baseUri, profileName, account.getAccountId())))
                    .header(NONCE_HEADER_NAME, generateNonce())
                    .header(RETRY_HEADER_NAME, account.getAcmeProfile().getRetryInterval().toString())
                    .body(accountDto);
        }

    }

    private void newAccountValidator(String profileName, String requestJson) throws AcmeProblemDocumentException {
        logger.info("Initiating the new Account validation for profile: {}", profileName);
        if (requestJson.isEmpty()) {
            logger.error("New Account is empty. JWS is malformed for profile: {}", profileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }
        initialize(requestJson);
        newAccountProcess();
    }

    private AcmeAccount addNewAccount(String profileName, String publicKey, Account accountRequest) throws AcmeProblemDocumentException {
        AcmeProfile acmeProfile;
        boolean isRa;
        RaProfile raProfileToUse;
        try {
            if (ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")) {
                raProfileToUse = getRaProfileEntity(profileName);
                acmeProfile = raProfileToUse.getAcmeProfile();

                isRa = true;
            } else {
                acmeProfile = getAcmeProfileEntityByName(profileName);
                raProfileToUse = acmeProfile.getRaProfile();
                isRa = false;
            }
        } catch (Exception e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("profileNotFound", "Profile not found", "The given profile is not found"));
        }
        if (logger.isDebugEnabled()) {
            logger.debug("RA Profile for new Account: {}, ACME Profile: {}", raProfileToUse.toString(), acmeProfile.toString());
        }
        String accountId = AcmeRandomGeneratorAndValidator.generateRandomId();
        AcmeAccount oldAccount = acmeAccountRepository.findByPublicKey(publicKey);
        if (acmeProfile.isRequireContact() != null && acmeProfile.isRequireContact() && accountRequest.getContact().isEmpty()) {
            logger.error("Contact not found for Account: {}", accountRequest);
            {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("invalidContact",
                        "Contact Information Not Found",
                        "Contact information is missing in the Request. It is set as mandatory for this profile"));
            }
        }

        if (acmeProfile.isRequireTermsOfService() != null && acmeProfile.isRequireTermsOfService() && accountRequest.isTermsOfServiceAgreed()) {
            logger.error("Terms of Service not agreed for the new Account: {}", accountRequest);
            {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("termsOfServiceNotAgreed",
                        "Terms of Service Not Agreed",
                        "Terms of Service not agreed by the client. It is set as mandatory for this profile"));
            }
        }

        if (!isRa && acmeProfile.getRaProfile() == null) {
            logger.error("RA Profile is not associated for the ACME Profile: {}", acmeProfile);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("invalidRaProfile",
                    "RA Profile Not Associated",
                    "RA Profile is not associated for the selected ACME profile"));
        }
        if (oldAccount == null) {
            if (accountRequest.isOnlyReturnExisting()) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST);
            }
        } else {
            return oldAccount;
        }
        AcmeAccount account = new AcmeAccount();
        account.setAcmeProfile(acmeProfile);
        account.setEnabled(true);
        account.setStatus(AccountStatus.VALID);
        account.setTermsOfServiceAgreed(true);
        account.setRaProfile(raProfileToUse);
        account.setPublicKey(publicKey);
        account.setDefaultRaProfile(true);
        account.setAccountId(accountId);
        account.setContact(SerializationUtil.serialize(accountRequest.getContact()));
        acmeAccountRepository.save(account);
        logger.debug("ACME Account created: {}", account);
        return account;
    }

    protected ResponseEntity<Order> processNewOrder(String profileName, String requestJson) throws AcmeProblemDocumentException {
        logger.info("Request to process new Order for profile: {}", profileName);
        initialize(requestJson);
        String[] acmeAccountKeyIdSegment = getJwsObject().getHeader().getKeyID().split("/");
        String acmeAccountId = acmeAccountKeyIdSegment[acmeAccountKeyIdSegment.length - 1];
        logger.info("ACME Account ID: {}", acmeAccountId);
        AcmeAccount acmeAccount;
        try {
             acmeAccount = getAcmeAccountEntity(acmeAccountId);
             logger.info("ACME Account set: {}", acmeAccount.toString());
        }catch (Exception e){
            logger.error("Requested Account with ID {} does not exists", acmeAccountId);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST);
        }
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String baseUrl;
        if (ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")) {
            baseUrl = String.format("%s/acme/raProfile/%s", baseUri, profileName);
        } else {
            baseUrl = String.format("%s/acme/%s", baseUri, profileName);
        }

        if(!acmeAccount.getStatus().equals(AccountStatus.VALID)){
            logger.error("Account status is not valid: {}", acmeAccount.getStatus().toString());
            throw new AcmeProblemDocumentException(HttpStatus.UNAUTHORIZED, Problem.UNAUTHORIZED, "Account is " + acmeAccount.getStatus().toString());
        }

        try {
            setPublicKey(AcmePublicKeyProcessor.publicKeyObjectFromString(acmeAccount.getPublicKey()));
            IsValidSignature();
            AcmeOrder order = generateOrder(baseUrl, acmeAccount);
            logger.debug("Order created: {}", order.toString());
            return ResponseEntity
                    .created(URI.create(order.getUrl()))
                    .header(NONCE_HEADER_NAME, generateNonce())
                    .header(RETRY_HEADER_NAME, order.getAcmeAccount().getAcmeProfile().getRetryInterval().toString())
                    .body(order.mapToDto());
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    protected AcmeOrder getAcmeOrderEntity(String orderId) throws AcmeProblemDocumentException {
        logger.info("Gathering ACME Order details with ID: {}", orderId);
        AcmeOrder acmeOrder = acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("orderNotFound", "Order Not Found", "Specified ACME Order not found")));
        logger.debug("Order: {}", orderId, acmeOrder.toString());
        return acmeOrder;
    }

    private X509Certificate getX509(String certificate) throws CertificateException {
        return CertificateUtil.getX509Certificate(certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", ""));
    }

    protected String frameCertChainString(List<Certificate> certificates) throws CertificateException {
        List<String> chain = new ArrayList<>();
        for (Certificate certificate : certificates) {
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
        logger.info("Generating new Order for Account: {}", acmeAccount.toString());
        Order orderRequest = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), Order.class);
        logger.debug("Order requested: {}", orderRequest.toString());
        AcmeOrder order = new AcmeOrder();
        order.setAcmeAccount(acmeAccount);
        order.setOrderId(AcmeRandomGeneratorAndValidator.generateRandomId());
        order.setStatus(OrderStatus.PENDING);
        order.setNotAfter(AcmeCommonHelper.getDateFromString(orderRequest.getNotAfter()));
        order.setNotBefore(AcmeCommonHelper.getDateFromString(orderRequest.getNotBefore()));
        order.setIdentifiers(SerializationUtil.serializeIdentifiers(orderRequest.getIdentifiers()));
        if(acmeAccount.getAcmeProfile().getValidity() != null) {
            order.setExpires(AcmeCommonHelper.addSeconds(new Date(), acmeAccount.getAcmeProfile().getValidity()));
        }else{
            order.setExpires(AcmeCommonHelper.getDefaultExpires());
        }
        acmeOrderRepository.save(order);
        logger.debug("Order created: {}", order);
        Set<AcmeAuthorization> authorizations = generateValidations(baseUrl, order, orderRequest.getIdentifiers());
        order.setAuthorizations(authorizations);
        logger.debug("Challenges created and are associated with the Authorization for Order: {}",order);
        return order;
    }

    protected AcmeChallenge validateChallenge(String challengeId) throws AcmeProblemDocumentException{
        logger.info("Initiating certificate validation for the Challenge ID: {}", challengeId);
        AcmeChallenge challenge;
        try {
            challenge = acmeChallengeRepository.findByChallengeId(challengeId).orElseThrow(() -> new NotFoundException(Challenge.class, challengeId));
            logger.debug("Challenge: {}", challenge.toString());

        } catch (NotFoundException e) {
            logger.error("Challenge not found with ID: {}", challengeId);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("challengeNotFound","Challenge Not Found","The requested challenge is not found"));
        }
        AcmeAuthorization authorization = challenge.getAuthorization();
        logger.debug("Authorization corresponding to the Order: {}", authorization.toString());
        AcmeOrder order = authorization.getOrder();
        logger.debug("Order corresponding to the Challenge: {}", order.toString());
        boolean isValid;
        if (challenge.getType().equals(ChallengeType.HTTP01)) {
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
        logger.info("Validation of the Challenge is completed: {}", challenge);
        return challenge;
    }

    public AcmeOrder checkOrderForFinalize(String orderId) throws AcmeProblemDocumentException {
        logger.info("Request to finalize the Order with ID: {}", orderId);
        AcmeOrder order;
        try {
            order = acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException(Order.class, orderId));
            logger.debug("Found Order: {}", order.toString());
        } catch (NotFoundException e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("orderNotFound","Order Not Found","The given Order is not found"));
        }
        if ( !order.getStatus().equals(OrderStatus.READY)) {
            logger.error("Order status: {}", order.getStatus());
            throw new AcmeProblemDocumentException(HttpStatus.FORBIDDEN, Problem.ORDER_NOT_READY);
        }
        return order;
    }

    @Async
    public void finalizeOrder(AcmeOrder order) throws AcmeProblemDocumentException {
        CertificateFinalizeRequest request = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), CertificateFinalizeRequest.class);
        logger.debug("Finalize Order: {}", request.toString());
        JcaPKCS10CertificationRequest p10Object;
        try {
            p10Object = new JcaPKCS10CertificationRequest(Base64.getUrlDecoder().decode(request.getCsr()));
            validateCSR(p10Object, order);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_CSR);
        }
        logger.info("Initiating issue certificate for Order: {}", order);
        ClientCertificateSignRequestDto certificateSignRequestDto = new ClientCertificateSignRequestDto();
        certificateSignRequestDto.setAttributes(new ArrayList<>());
        certificateSignRequestDto.setPkcs10(request.getCsr());
        order.setStatus(OrderStatus.PROCESSING);
        acmeOrderRepository.save(order);
        createCert(order, certificateSignRequestDto);
    }

    @Async
    private void createCert(AcmeOrder order, ClientCertificateSignRequestDto certificateSignRequestDto) {
        if(logger.isDebugEnabled()) {
            logger.debug("Initiating the issuing Certificate for the Order: {} and certificate signing request: {}", order.toString(), certificateSignRequestDto.toString());
        }
        try {
            ClientCertificateDataResponseDto certificateOutput = clientOperationService.issueCertificate(order.getAcmeAccount().getRaProfile().getUuid(), certificateSignRequestDto, true);
            order.setCertificateId(AcmeRandomGeneratorAndValidator.generateRandomId());
            order.setCertificateReference(certificateService.getCertificateEntity(certificateOutput.getUuid()));
            order.setStatus(OrderStatus.VALID);
            order.setExpires(AcmeCommonHelper.getDefaultExpires());
        } catch (Exception e) {
            logger.error("Issue Certificate failed. Exception: {}", e.getMessage());
            order.setStatus(OrderStatus.INVALID);
        }
        acmeOrderRepository.save(order);
    }

    public ResponseEntity<List<Order>> listOrders(String accountId) throws AcmeProblemDocumentException {
        logger.info("Request to list Orders for the Account with ID: {}", accountId);
        List<Order> orders = getAcmeAccountEntity(accountId)
                .getOrders()
                .stream()
                .map(AcmeOrder::mapToDto)
                .collect(Collectors.toList());
        logger.debug("Number of Orders: {}", orders.size());
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, generateNonce())
                .body(orders);
    }

    public Authorization checkDeactivateAuthorization(String authorizationId) throws NotFoundException {
        boolean isDeactivateRequest = false;
        if (getJwsObject().getPayload().toJSONObject() != null) {
            isDeactivateRequest = getJwsObject().getPayload().toJSONObject().getOrDefault("status", "") == "deactivated";
        }
        AcmeAuthorization authorization = acmeAuthorizationRepository.findByAuthorizationId(authorizationId).orElseThrow(() -> new NotFoundException(Authorization.class, authorizationId));

        if (isDeactivateRequest) {
            authorization.setStatus(AuthorizationStatus.DEACTIVATED);
            acmeAuthorizationRepository.save(authorization);
        }
        return authorization.mapToDto();
    }

    public ResponseEntity<Account> updateAccount(String accountId) throws NotFoundException, AcmeProblemDocumentException {
        logger.info("Request to update the ACME Account with ID: {}", accountId);
        AcmeAccount account = getAcmeAccountEntity(accountId);
        Account request = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), Account.class);
        logger.debug("Account Update request: {}", request.toString());
        if (request.getContact() != null) {
            account.setContact(SerializationUtil.serialize(request.getContact()));
        }
        if (request.getStatus().equals(AccountStatus.DEACTIVATED)) {
            logger.info("Deactivating Account with ID: {}", accountId);
            deactivateOrders(account.getOrders());
            account.setStatus(AccountStatus.DEACTIVATED);
        }
        acmeAccountRepository.save(account);
        logger.debug("Updated Account: {}", account.mapToDto().toString());
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, generateNonce())
                .header(RETRY_HEADER_NAME, account.getAcmeProfile().getRetryInterval().toString())
                .body(account.mapToDto());
    }

    public ResponseEntity<?> revokeCertificate() throws ConnectorException, CertificateException, AcmeProblemDocumentException {
        CertificateRevocationRequest request = AcmeJsonProcessor.getPayloadAsRequestObject(getJwsObject(), CertificateRevocationRequest.class);
        logger.debug("Certificate revocation is triggered with the payload: {}", request.toString());
        X509Certificate x509Certificate = (X509Certificate) CertificateFactory.getInstance(CERTIFICATE_TYPE)
                .generateCertificate(new ByteArrayInputStream(Base64.getUrlDecoder().decode(request.getCertificate())));
        String decodedCertificate = X509ObjectToString.toPem(x509Certificate).replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", "");
        ClientCertificateRevocationDto revokeRequest = new ClientCertificateRevocationDto();
        Certificate cert = certificateService.getCertificateEntityByContent(decodedCertificate);
        if (cert.getStatus().equals(CertificateStatus.REVOKED)) {
            logger.error("Certificate is already revoked: {}", cert);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ALREADY_REVOKED);
        }
        String pemPubKeyJws = "";
        if (getJwsObject().getHeader().toJSONObject().containsKey("jwk")) {
            pemPubKeyJws = AcmePublicKeyProcessor.publicKeyPemStringFromObject(publicKey);
        }
        PublicKey accountPublicKey;
        PublicKey certPublicKey;
        String accountKid = getJwsObject().getHeader().toJSONObject().get("kid").toString();
        logger.info("kid of the Account for revocation: {}", accountKid);
        if (getJwsObject().getHeader().toJSONObject().containsKey("kid")) {
            String accountId = accountKid.split("/")[accountKid.split("/").length - 1];
            AcmeAccount account = getAcmeAccountEntity(accountId);
            try {
                accountPublicKey = AcmePublicKeyProcessor.publicKeyObjectFromString(account.getPublicKey());
            } catch (Exception e) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }
            certPublicKey = x509Certificate.getPublicKey();
        } else {
            accountPublicKey = publicKey;
            certPublicKey = x509Certificate.getPublicKey();

        }
        if (getJwsObject().getHeader().toJSONObject().containsKey("jwk")) {
            String pemPubKeyCert = AcmePublicKeyProcessor.publicKeyPemStringFromObject(certPublicKey);
            String pemPubKeyAcc = AcmePublicKeyProcessor.publicKeyPemStringFromObject(accountPublicKey);
            if (!pemPubKeyCert.equals(pemPubKeyJws) || pemPubKeyAcc.equals(pemPubKeyJws)) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }
        }
        try {
            if ((accountPublicKey != null && getJwsObject().verify(new RSASSAVerifier((RSAPublicKey) accountPublicKey)))) {
                logger.info("ACME Revocation request is signed by Account key: {}", request);
            } else if ((certPublicKey != null && getJwsObject().verify(new RSASSAVerifier((RSAPublicKey) certPublicKey)))) {
                logger.info("ACME Revocation request is signed by private key associated to the Certificate: {}", request);
            } else {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }
        } catch (JOSEException e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
        }

        revokeRequest.setReason(RevocationReason.fromCode(request.getReason().getCode()));
        revokeRequest.setAttributes(List.of());
        try {
            clientOperationService.revokeCertificate(cert.getRaProfile().getUuid(), cert.getUuid(), revokeRequest, true);
            return ResponseEntity
                    .ok()
                    .header(NONCE_HEADER_NAME, generateNonce())
                    .build();
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .header(NONCE_HEADER_NAME, generateNonce())
                    .build();
        }
    }

    public ResponseEntity<?> keyRollover() throws AcmeProblemDocumentException {
        JWSObject innerJws = getJwsObject().getPayload().toJWSObject();
        PublicKey newKey;
        PublicKey oldKey;
        try {
            newKey = ((RSAKey) innerJws.getHeader().getJWK()).toPublicKey();
            oldKey = ((RSAKey) (innerJws.getPayload().toJSONObject().get("oldKey"))).toPublicKey();
        } catch (JOSEException e) {
            logger.error("Error while parsing JWS: {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("malformed", "JWS Malformed", "JWS Malformed. Error while decoding the JWS Object"));
        }
        String account = innerJws.getPayload().toJSONObject().get("account").toString();
        String accountId = account.split("/")[account.split("/").length - 1];
        AcmeAccount acmeAccount = getAcmeAccountEntity(accountId);
        if(!acmeAccount.getPublicKey().equals(AcmePublicKeyProcessor.publicKeyPemStringFromObject(oldKey))){
            logger.error("Public key of the Account with ID: {} does not match with old key field in request", accountId);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("malformed", "JWS Malformed", "Account key does not match with old key"));
        }
        AcmeAccount oldAccount = acmeAccountRepository.findByPublicKey(AcmePublicKeyProcessor.publicKeyPemStringFromObject(newKey));
        if(oldAccount != null){
            return ResponseEntity.status(HttpStatus.CONFLICT).header("Location",oldAccount.getAccountId()).body(new ProblemDocument("keyExists", "New Key already exists", "New key to replace the old account is already available in the server and is tagged to a different account"));
        }
        validateKey(innerJws);
        acmeAccount.setPublicKey(AcmePublicKeyProcessor.publicKeyPemStringFromObject(newKey));
        acmeAccountRepository.save(acmeAccount);
        return ResponseEntity.ok().build();
    }

    private void validateKey(JWSObject innerJws) throws AcmeProblemDocumentException {
        if(!innerJws.getHeader().toJSONObject().containsKey("jwk")){
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("malformed","Inner JWS Malformed", "Inner JWS does not contain jwk"));
        }
        if(!innerJws.getHeader().toJSONObject().getOrDefault("url","innerUrl").equals(getJwsObject().getHeader().toJSONObject().getOrDefault("url","outerUrl"))){
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("malformed","Inner JWS Malformed", "URL in inner and outer jws are different"));
        }
        if(innerJws.getHeader().toJSONObject().containsKey("nonce")){
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("malformed","Inner JWS Malformed", "Inner JWS cannot contain nonce header parameter"));
        }

    }

    private void deactivateOrders(Set<AcmeOrder> orders) {
        for (AcmeOrder order : orders) {
            order.setStatus(OrderStatus.INVALID);
            deactivateAuthorizations(order.getAuthorizations());
            acmeOrderRepository.save(order);
        }
    }

    private void deactivateAuthorizations(Set<AcmeAuthorization> authorizations) {
        for (AcmeAuthorization authorization : authorizations) {
            authorization.setStatus(AuthorizationStatus.DEACTIVATED);
            deactivateChallenges(authorization.getChallenges());
            acmeAuthorizationRepository.save(authorization);
        }
    }

    private void deactivateChallenges(Set<AcmeChallenge> challenges) {
        for (AcmeChallenge challenge : challenges) {
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
        if(acmeOrder.getAcmeAccount().getAcmeProfile().getValidity() != null) {
            authorization.setExpires(AcmeCommonHelper.addSeconds(new Date(), acmeOrder.getAcmeAccount().getAcmeProfile().getValidity()));
        }else{
            authorization.setExpires(AcmeCommonHelper.getDefaultExpires());
        }
        authorization.setWildcard(checkWildcard(identifiers));
        authorization.setIdentifier(SerializationUtil.serialize(identifiers.get(0)));
        acmeAuthorizationRepository.save(authorization);
        AcmeChallenge dnsChallenge = generateChallenge(ChallengeType.DNS01, baseUrl, authorization);
        AcmeChallenge httpChallenge = generateChallenge(ChallengeType.HTTP01, baseUrl, authorization);
        authorization.setChallenges(Set.of(dnsChallenge, httpChallenge));
        return authorization;
    }

    private AcmeChallenge generateChallenge(ChallengeType challengeType, String baseUrl, AcmeAuthorization authorization) {
        logger.info("Generating new Challenge for Authorization: {}", authorization.toString());
        AcmeChallenge challenge = new AcmeChallenge();
        challenge.setChallengeId(AcmeRandomGeneratorAndValidator.generateRandomId());
        challenge.setStatus(ChallengeStatus.PENDING);
        challenge.setToken(AcmeRandomGeneratorAndValidator.generateRandomTokenForValidation(publicKey));
        challenge.setAuthorization(authorization);
        challenge.setType(challengeType);
        acmeChallengeRepository.save(challenge);
        return challenge;
    }

    private AcmeAccount getAcmeAccountEntity(String accountId) throws AcmeProblemDocumentException {
        return acmeAccountRepository.findByAccountId(accountId).orElseThrow(() -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST));
    }

    private AcmeProfile getAcmeProfileEntityByName(String profileName) throws AcmeProblemDocumentException {
        if (acmeProfileRepository.existsByName(profileName)) {
            return acmeProfileRepository.findByName(profileName);
        } else {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("profileNotFound","ACME Profile Not Found","Given ACME Profile is not found"));
        }
    }

    private boolean checkWildcard(List<Identifier> identifiers) {
        return !identifiers.stream().filter(identifier -> identifier.getValue().contains("*")).collect(Collectors.toList()).isEmpty();
    }

    private String generateDnsValidationToken(String publicKey, String token) {
        MessageDigest digest;
        try {
            PublicKey pubKey = AcmePublicKeyProcessor.publicKeyObjectFromString(publicKey);
            digest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
            final byte[] encodedhashOfExpectedKeyAuthorization = digest.digest(AcmeCommonHelper.createKeyAuthorization(token, pubKey).getBytes(StandardCharsets.UTF_8));
            final String base64EncodedDigest = Base64URL.encode(encodedhashOfExpectedKeyAuthorization).toString();
            return base64EncodedDigest;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return null;
        }
    }

    private boolean validateHttpChallenge(AcmeChallenge challenge) throws AcmeProblemDocumentException {
        logger.info("Initiating HTTP-01 Challenge validation: {}", challenge.toString());
        String response = getHttpChallengeResponse(
                SerializationUtil.deserializeIdentifier(
                                challenge
                                        .getAuthorization()
                                        .getIdentifier()
                        )
                        .getValue().replace("*.",""),
                challenge.getToken());
        PublicKey pubKey;
        try {
            pubKey = AcmePublicKeyProcessor.publicKeyObjectFromString(challenge.getAuthorization().getOrder().getAcmeAccount().getPublicKey());
        } catch (Exception e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.SERVER_INTERNAL);
        }
        String expectedResponse = AcmeCommonHelper.createKeyAuthorization(challenge.getToken(), pubKey);
        logger.debug("Response from the server: {}, expected response: {}", response, expectedResponse);
        if (response.equals(expectedResponse)) {
            return true;
        }
        return false;
    }

    private boolean validateDnsChallenge(AcmeChallenge challenge) {
        logger.info("Initiating DNS-01 Validation for challenge: {}", challenge.toString());
        Properties env = new Properties();
        env.setProperty(Context.INITIAL_CONTEXT_FACTORY, DNS_CONTENT_FACTORY);
        AcmeProfile acmeProfile = challenge.getAuthorization().getOrder().getAcmeAccount().getAcmeProfile();
        if (acmeProfile.getDnsResolverIp() == null || acmeProfile.getDnsResolverIp().isEmpty()) {
            env.setProperty(Context.PROVIDER_URL, DNS_ENV_PREFIX);
        } else {
            env.setProperty(Context.PROVIDER_URL, DNS_ENV_PREFIX + acmeProfile.getDnsResolverIp() + ":" + Optional.ofNullable(acmeProfile.getDnsResolverPort())
                    .orElse(DEFAULT_DNS_PORT));
        }
        List<String> txtRecords = new ArrayList<>();
        String expectedKeyAuthorization = generateDnsValidationToken(challenge.getAuthorization().getOrder().getAcmeAccount().getPublicKey(), challenge.getToken());
        DirContext context;
        try {
            context = new InitialDirContext(env);
            Attributes list = context.getAttributes(DNS_ACME_PREFIX
                            + SerializationUtil.deserializeIdentifier(
                            challenge.getAuthorization().getIdentifier()).getValue(),
                    new String[]{DNS_RECORD_TYPE});
            NamingEnumeration<? extends javax.naming.directory.Attribute> records = list.getAll();

            while (records.hasMore()) {
                javax.naming.directory.Attribute record = records.next();
                txtRecords.add(record.get().toString());
            }
        } catch (NamingException e) {
            logger.error(e.getMessage());
        }
        if (txtRecords.isEmpty()) {
            logger.error("TXT record is empty for Challenge: {}", challenge);
            return false;
        }
        if (!txtRecords.contains(expectedKeyAuthorization)) {
            logger.error("TXT record not found for Challenge: {}", challenge);
            return false;
        }
        return true;
    }

    private String getHttpChallengeResponse(String domain, String token) throws AcmeProblemDocumentException {
        return getResponseFollowRedirects(String.format("http://%s/.well-known/acme-challenge/%s", domain, token));
    }

    private String getResponseFollowRedirects(String url) throws AcmeProblemDocumentException {
        String finalUrl = url;
        String acmeChallengeOutput = "";
        Integer redirectFollowCount = 0;
        try {
            HttpURLConnection connection;
            do {
                redirectFollowCount += 1;
                URL urlObject = new URL(finalUrl);
                if (!(urlObject.getPort() == 80 || urlObject.getPort() == 443 || urlObject.getPort() == -1)) {
                    throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                            new ProblemDocument("invalidRedirect",
                                    "Invalid Redirect",
                                    "Only 80 and 443 ports can be followed"));
                }
                connection = (HttpURLConnection) new URL(finalUrl).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestMethod(HTTP_CHALLENGE_REQUEST_METHOD);
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    acmeChallengeOutput = bufferedReader.lines().collect(Collectors.joining());
                }
                if (responseCode >= 300 && responseCode < 400) {
                    String redirectedUrl = connection.getHeaderField(LOCATION_HEADER_NAME);
                    if (null == redirectedUrl) {
                        break;
                    }
                    finalUrl = redirectedUrl;
                } else
                    break;
            } while (connection.getResponseCode() != HttpURLConnection.HTTP_OK && redirectFollowCount < MAX_REDIRECT_COUNT);
            connection.disconnect();
        } catch (AcmeProblemDocumentException e){
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return acmeChallengeOutput;
    }

    public String generateNonce() {
        String nonceString = AcmeRandomGeneratorAndValidator.generateNonce();
        Date expires = AcmeCommonHelper.addSeconds(new Date(), NONCE_VALIDITY);
        AcmeNonce acmeNonce = new AcmeNonce();
        acmeNonce.setCreated(new Date());
        acmeNonce.setNonce(nonceString);
        acmeNonce.setExpires(expires);
        acmeNonceRepository.save(acmeNonce);
        return nonceString;
    }

    private void acmeNonceCleanup() {
        try {
            acmeNonceRepository.deleteAll(acmeNonceRepository.findAllByExpiresBefore(new Date()));
        }catch (Exception e){
            logger.error(e.getMessage());
        }
    }

    public void isNonceValid(String nonce) throws AcmeProblemDocumentException {
        acmeNonceCleanup();
        AcmeNonce acmeNonce = acmeNonceRepository.findByNonce(nonce).orElseThrow(() -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_NONCE));
        if (acmeNonce.getExpires().after(AcmeCommonHelper.addSeconds(new Date(), NONCE_VALIDITY))) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_NONCE);
        }
    }

    public void validateCSR(JcaPKCS10CertificationRequest csr, AcmeOrder order) throws AcmeProblemDocumentException {
        List<String> sans = new ArrayList<>();
        List<String> dnsIdentifiers = new ArrayList<>();

        Attribute[] certAttributes = csr.getAttributes();
        try {
            String commonName = IETFUtils.valueToString(csr.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue());
            if (commonName != null && !commonName.isEmpty()) {
                sans.add(commonName);
                dnsIdentifiers.add(commonName);
            }

        } catch (Exception e) {
            logger.warn("Unable to find common name: {}", e.getMessage());
        }
        for (Attribute attribute : certAttributes) {
            if (attribute.getAttrType().equals(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                Extensions extensions = Extensions.getInstance(attribute.getAttrValues().getObjectAt(0));
                GeneralNames gns = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
                if (gns != null) {
                    GeneralName[] names = gns.getNames();
                    for (GeneralName name : names) {
                        if (name.getTagNo() == GeneralName.dNSName) {
                            dnsIdentifiers.add(IETFUtils.valueToString(name.getName()));
                        }
                        sans.add(IETFUtils.valueToString(name.getName()));
                    }
                }
            }
        }

        List<String> identifiers = SerializationUtil.deserializeIdentifiers(order.getIdentifiers())
                .stream()
                .map(Identifier::getValue)
                .collect(Collectors.toList());

        List<String> identifiersDns = new ArrayList<>();
        for (Identifier iden : SerializationUtil.deserializeIdentifiers(order.getIdentifiers())) {
            if (iden.getType().equals("dns")) {
                identifiersDns.add(iden.getValue());
            }
        }

        if (!sans.containsAll(identifiers)) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_CSR);
        }
        if (!dnsIdentifiers.containsAll(identifiersDns)) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_CSR);
        }
    }
}
