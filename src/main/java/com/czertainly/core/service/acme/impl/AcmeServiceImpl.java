package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.api.model.core.authority.CertificateRevocationReason;
import com.czertainly.api.model.core.certificate.CertificateChainResponseDto;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.*;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.*;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.acme.AcmeConstants;
import com.czertainly.core.service.acme.AcmeService;
import com.czertainly.core.service.acme.message.AcmeJwsRequest;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.*;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
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
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcmeServiceImpl implements AcmeService {

    private static final Logger logger = LoggerFactory.getLogger(AcmeServiceImpl.class);

    private AcmeNonceRepository acmeNonceRepository;
    private RaProfileRepository raProfileRepository;
    private AcmeProfileRepository acmeProfileRepository;
    private AcmeAccountRepository acmeAccountRepository;
    private AcmeOrderRepository acmeOrderRepository;
    private AcmeAuthorizationRepository acmeAuthorizationRepository;
    private AcmeChallengeRepository acmeChallengeRepository;
    private ClientOperationService clientOperationService;
    private CertificateService certificateService;

    @Autowired
    public void setAcmeNonceRepository(AcmeNonceRepository acmeNonceRepository) {
        this.acmeNonceRepository = acmeNonceRepository;
    }

    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) {
        this.raProfileRepository = raProfileRepository;
    }

    @Autowired
    public void setAcmeProfileRepository(AcmeProfileRepository acmeProfileRepository) {
        this.acmeProfileRepository = acmeProfileRepository;
    }

    @Autowired
    public void setAcmeAccountRepository(AcmeAccountRepository acmeAccountRepository) {
        this.acmeAccountRepository = acmeAccountRepository;
    }

    @Autowired
    public void setAcmeOrderRepository(AcmeOrderRepository acmeOrderRepository) {
        this.acmeOrderRepository = acmeOrderRepository;
    }

    @Autowired
    public void setAcmeAuthorizationRepository(AcmeAuthorizationRepository acmeAuthorizationRepository) {
        this.acmeAuthorizationRepository = acmeAuthorizationRepository;
    }

    @Autowired
    public void setAcmeChallengeRepository(AcmeChallengeRepository acmeChallengeRepository) {
        this.acmeChallengeRepository = acmeChallengeRepository;
    }

    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) {
        this.clientOperationService = clientOperationService;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }


    @Override
    public ResponseEntity<Directory> getDirectory(String acmeProfileName) throws AcmeProblemDocumentException {
        logger.debug("Gathering Directory information for ACME: {}", acmeProfileName);

        Directory directory = new Directory();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + AcmeConstants.ACME_URI_HEADER;
        String replaceUrl;
        if (isRaProfileBased()) {
            replaceUrl = "%s/raProfile/%s/";
        } else {
            replaceUrl = "%s/%s/";
        }
        directory.setNewNonce(String.format(replaceUrl + "new-nonce", baseUri, acmeProfileName));
        directory.setNewAccount(String.format(replaceUrl + "new-account", baseUri, acmeProfileName));
        directory.setNewOrder(String.format(replaceUrl + "new-order", baseUri, acmeProfileName));
        directory.setNewAuthz(String.format(replaceUrl + "new-authz", baseUri, acmeProfileName));
        directory.setRevokeCert(String.format(replaceUrl + "revoke-cert", baseUri, acmeProfileName));
        directory.setKeyChange(String.format(replaceUrl + "key-change", baseUri, acmeProfileName));
        try {
            directory.setMeta(frameDirectoryMeta(acmeProfileName));
        } catch (NotFoundException e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("profileNotFound", "Profile Not Found", "Given profile name is not found"));
        }
        logger.debug("Directory information retrieved: {}", directory);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .body(directory);
    }

    @Override
    public ResponseEntity<?> getNonce(String acmeProfileName, Boolean isHead) {
        String nonce = generateNonce();
        logger.debug("New Nonce: {}", nonce);
        ResponseEntity.HeadersBuilder<?> responseBuilder;
        if (isHead) {
            responseBuilder = ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore());
        } else {
            responseBuilder = ResponseEntity.noContent()
                    .cacheControl(CacheControl.noStore());
        }

        return responseBuilder
                .header(AcmeConstants.NONCE_HEADER_NAME, nonce)
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .build();
    }

    @Override
    public ResponseEntity<Account> newAccount(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            logger.error("New Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        // Parse and check the JWS request
        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);
        jwsRequest.readPublicKey();
        jwsRequest.checkSignature();

        NewAccountRequest accountRequest = AcmeJsonProcessor.getPayloadAsRequestObject(jwsRequest.getJwsObject(), NewAccountRequest.class);
        logger.debug("New Account requested: {}", accountRequest.toString());
        AcmeAccount account = addNewAccount(acmeProfileName, AcmePublicKeyProcessor.publicKeyPemStringFromObject(jwsRequest.getPublicKey()), accountRequest);
        Account accountDto = account.mapToDto();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + AcmeConstants.ACME_URI_HEADER;

        ResponseEntity.BodyBuilder responseBuilder;
        if (isRaProfileBased()) {
            accountDto.setOrders(String.format("%s/raProfile/%s/acct/%s/orders", baseUri, acmeProfileName, account.getAccountId()));
            if (accountRequest.isOnlyReturnExisting()) {
                responseBuilder = ResponseEntity.ok()
                        .location(URI.create(String.format("%s/raProfile/%s/acct/%s", baseUri, acmeProfileName, account.getAccountId())));
            } else {
                responseBuilder = ResponseEntity
                        .created(URI.create(String.format("%s/raProfile/%s/acct/%s", baseUri, acmeProfileName, account.getAccountId())));
            }
        } else {
            accountDto.setOrders(String.format("%s/%s/acct/%s/orders", baseUri, acmeProfileName, account.getAccountId()));
            if (accountRequest.isOnlyReturnExisting()) {
                responseBuilder = ResponseEntity.ok()
                        .location(URI.create(String.format("%s/%s/acct/%s", baseUri, acmeProfileName, account.getAccountId())));
            } else {
                responseBuilder = ResponseEntity
                        .created(URI.create(String.format("%s/%s/acct/%s", baseUri, acmeProfileName, account.getAccountId())));
            }
        }

        return responseBuilder
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.RETRY_HEADER_NAME, account.getAcmeProfile().getRetryInterval().toString())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(accountDto);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String acmeProfileName, String accountId, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            logger.error("Update Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        // Parse and check the JWS request
        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);

        logger.debug("Request to update the ACME Account with ID: {}", accountId);
        AcmeAccount account;
        try {
            account = getAcmeAccountEntity(accountId);
        } catch (NotFoundException e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST);
        }

        validateAccount(account);

        Account request = AcmeJsonProcessor.getPayloadAsRequestObject(jwsRequest.getJwsObject(), Account.class);
        logger.debug("Account Update request: {}", request.toString());
        if (request.getContact() != null) {
            account.setContact(SerializationUtil.serialize(request.getContact()));
        }
        if (request.getStatus() != null && request.getStatus().equals(AccountStatus.DEACTIVATED)) {
            logger.info("Deactivating Account with ID: {}", accountId);
            deactivateOrders(account.getOrders());
            account.setStatus(AccountStatus.DEACTIVATED);
        }
        acmeAccountRepository.save(account);
        if (logger.isDebugEnabled()) {
            logger.debug("Updated Account: {}", account.mapToDto().toString());
        }

        return ResponseEntity.ok()
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.RETRY_HEADER_NAME, account.getAcmeProfile().getRetryInterval().toString())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(account.mapToDto());
    }

    @Override
    public ResponseEntity<?> keyRollover(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            logger.error("Update Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        // Parse and check the JWS request
        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);

        JWSObject innerJws = jwsRequest.getJwsObject().getPayload().toJWSObject();
        PublicKey newKey;
        PublicKey oldKey;
        // TODO: Handle EC keys
        try {
            newKey = ((RSAKey) innerJws.getHeader().getJWK()).toPublicKey();
            oldKey = ((RSAKey) (innerJws.getPayload().toJSONObject().get("oldKey"))).toPublicKey();
        } catch (JOSEException e) {
            logger.error("Error while parsing JWS: {}", e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("malformed", "JWS Malformed", "JWS Malformed. Error while decoding the JWS Object"));
        }

        String account = innerJws.getPayload().toJSONObject().get("account").toString();
        String accountId = account.split("/")[account.split("/").length - 1];

        AcmeAccount acmeAccount;
        try {
            acmeAccount = getAcmeAccountEntity(accountId);
        } catch (NotFoundException e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST);
        }

        validateAccount(acmeAccount);

        if (!acmeAccount.getPublicKey().equals(AcmePublicKeyProcessor.publicKeyPemStringFromObject(oldKey))) {
            logger.error("Public key of the Account with ID: {} does not match with old key in request", accountId);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("malformed", "JWS Malformed", "Account key does not match with old key"));
        }
        AcmeAccount oldAccount = acmeAccountRepository.findByPublicKey(AcmePublicKeyProcessor.publicKeyPemStringFromObject(newKey));
        if (oldAccount != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).header(AcmeConstants.LOCATION_HEADER_NAME, oldAccount.getAccountId()).body(new ProblemDocument("keyExists", "New Key already exists", "New key already tagged to a different account"));
        }

        validateKey(jwsRequest.getJwsObject(), innerJws);

        acmeAccount.setPublicKey(AcmePublicKeyProcessor.publicKeyPemStringFromObject(newKey));
        acmeAccountRepository.save(acmeAccount);

        return ResponseEntity.ok()
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .build();
    }

    @Override
    public ResponseEntity<Order> newOrder(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            logger.error("Update Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        // Parse and check the JWS request
        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);

        String[] acmeAccountKeyIdSegment = jwsRequest.getJwsObject().getHeader().getKeyID().split("/");
        String acmeAccountId = acmeAccountKeyIdSegment[acmeAccountKeyIdSegment.length - 1];
        logger.info("ACME Account ID: {}", acmeAccountId);
        AcmeAccount acmeAccount;
        try {
            acmeAccount = getAcmeAccountEntity(acmeAccountId);
            validateAccount(acmeAccount);
            logger.info("ACME Account set: {}", acmeAccount);
        } catch (NotFoundException e) {
            logger.error("Requested Account with ID {} does not exists", acmeAccountId);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST);
        }

        if (!acmeAccount.getStatus().equals(AccountStatus.VALID)) {
            logger.error("Account status is not valid: {}", acmeAccount.getStatus().toString());
            throw new AcmeProblemDocumentException(HttpStatus.UNAUTHORIZED, Problem.UNAUTHORIZED, "Account is " + acmeAccount.getStatus().toString());
        }

        AcmeOrder order = generateOrder(acmeAccount, jwsRequest);
        logger.debug("Order created: {}", order);

        return ResponseEntity.created(URI.create(order.getUrl()))
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.RETRY_HEADER_NAME, order.getAcmeAccount().getAcmeProfile().getRetryInterval().toString())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String acmeProfileName, String accountId) throws AcmeProblemDocumentException {
        AcmeAccount acmeAccount;
        try {
            acmeAccount = getAcmeAccountEntity(accountId);
        } catch (NotFoundException e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST);
        }

        updateOrderStatusForAccount(acmeAccount);

        logger.debug("Request to list Orders for the Account with ID: {}", accountId);
        List<Order> orders = acmeAccount
                .getOrders()
                .stream()
                .map(AcmeOrder::mapToDto)
                .collect(Collectors.toList());
        logger.debug("Number of Orders: {}", orders.size());

        return ResponseEntity.ok()
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(orders);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorization(String acmeProfileName, String authorizationId, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            logger.error("Update Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        // Parse and check the JWS request
        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);

        boolean isDeactivateRequest = false;
        if (jwsRequest.getJwsObject().getPayload().toJSONObject() != null) {
            isDeactivateRequest = jwsRequest.getJwsObject().getPayload().toJSONObject().getOrDefault("status", "") == "deactivated";
        }
        // TODO: Check response for not found authorization
        AcmeAuthorization authorization = acmeAuthorizationRepository.findByAuthorizationId(
                authorizationId).orElseThrow(() -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                new ProblemDocument("authorizationNotFound", "Authorization Not Found", "The authorization is not found")));

        if (authorization.getExpires() != null && authorization.getExpires().before(new Date())) {
            authorization.setStatus(AuthorizationStatus.INVALID);
            acmeAuthorizationRepository.save(authorization);
        }
        if (isDeactivateRequest) {
            authorization.setStatus(AuthorizationStatus.DEACTIVATED);
            acmeAuthorizationRepository.save(authorization);
        }

        Authorization authorizationDto = authorization.mapToDto();
        logger.debug("Authorization: {}", authorizationDto.toString());

        return ResponseEntity.ok()
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(authorizationDto);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId) throws AcmeProblemDocumentException {
        logger.debug("Validating Challenge with ID {}:", challengeId);
        AcmeChallenge challenge;
        try {
            challenge = acmeChallengeRepository.findByChallengeId(challengeId).orElseThrow(() -> new NotFoundException(Challenge.class, challengeId));
            validateAccount(challenge.getAuthorization().getOrder().getAcmeAccount());
            logger.debug("Challenge: {}", challenge);

        } catch (NotFoundException e) {
            logger.error("Challenge not found with ID: {}", challengeId);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("challengeNotFound", "Challenge Not Found", "The requested challenge is not found"));
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

        acmeOrderRepository.save(order);
        acmeChallengeRepository.save(challenge);
        acmeAuthorizationRepository.save(authorization);

        logger.debug("Validation of the Challenge is completed: {}", challenge);

        return ResponseEntity.ok()
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(challenge.mapToDto());
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String acmeProfileName, String orderId, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            logger.error("Update Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        // Parse and check the JWS request
        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);

        logger.debug("Request to finalize the Order with ID: {}", orderId);
        AcmeOrder order;

        order = acmeOrderRepository.findByOrderId(orderId).orElseThrow(
                () -> new AcmeProblemDocumentException(
                        HttpStatus.BAD_REQUEST,
                        new ProblemDocument("orderNotFound", "Order Not Found", "The given Order is not found")));
        validateAccount(order.getAcmeAccount());
        logger.debug("Order found : {}", order);

        if (!order.getStatus().equals(OrderStatus.READY)) { // A request to finalize an order will result in error if the order is not in the "ready" state
            logger.error("Cannot finalize Order that is not ready.");
            throw new AcmeProblemDocumentException(HttpStatus.FORBIDDEN, Problem.ORDER_NOT_READY);
        }

        // Now finalize the order
        finalizeOrder(order, jwsRequest);

        return ResponseEntity.ok()
                .location(URI.create(order.getUrl()))
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.RETRY_HEADER_NAME, order.getAcmeAccount().getAcmeProfile().getRetryInterval().toString())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(order.mapToDto());
    }

    @Async("threadPoolTaskExecutor")
    protected void finalizeOrder(AcmeOrder order, AcmeJwsRequest jwsRequest) throws AcmeProblemDocumentException {
        logger.debug("Finalizing Order with ID: {}", order.getOrderId());
        CertificateFinalizeRequest request = AcmeJsonProcessor.getPayloadAsRequestObject(jwsRequest.getJwsObject(), CertificateFinalizeRequest.class);
        logger.debug("Finalize Order request: {}", request);

        JcaPKCS10CertificationRequest p10Object;
        String decodedCsr;
        try {
            p10Object = new JcaPKCS10CertificationRequest(Base64.getUrlDecoder().decode(request.getCsr()));
            validateCSR(p10Object, order);
            decodedCsr = JcaPKCS10CertificationRequestToString(p10Object);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_CSR);
        }

        decodedCsr = decodedCsr.replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replace("-----BEGIN NEW CERTIFICATE REQUEST-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("-----END CERTIFICATE REQUEST-----", "")
                .replace("-----END NEW CERTIFICATE REQUEST-----", "");

        logger.debug("Initiating issue Certificate for Order with ID: {}", order.getOrderId());
        ClientCertificateSignRequestDto certificateSignRequestDto = new ClientCertificateSignRequestDto();
        certificateSignRequestDto.setAttributes(getClientOperationAttributes(false, order.getAcmeAccount()));
        certificateSignRequestDto.setPkcs10(decodedCsr);
        order.setStatus(OrderStatus.PROCESSING);
        acmeOrderRepository.save(order);
        createCert(order, certificateSignRequestDto);
    }

    @Override
    public ResponseEntity<Order> getOrder(String acmeProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException {
        AcmeOrder order = getAcmeOrderEntity(orderId);

        updateOrderStatusByExpiry(order);

        if (order.getStatus().equals(OrderStatus.INVALID)) {
            logger.error("Order status is invalid: {}", order);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.SERVER_INTERNAL);
        }

        return ResponseEntity.ok()
                .location(URI.create(order.getUrl()))
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.RETRY_HEADER_NAME, order.getAcmeAccount().getAcmeProfile().getRetryInterval().toString())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws NotFoundException, CertificateException {
        logger.debug("Downloading the Certificate with ID: {}", certificateId);
        ByteArrayResource byteArrayResource = getCertificateResource(certificateId);

        return ResponseEntity.ok()
                .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                .contentType(MediaType.valueOf("application/pem-certificate-chain"))
                .body(byteArrayResource);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, ConnectorException, CertificateException {
        if (requestJson.isEmpty()) {
            logger.error("Update Account request is empty. JWS is malformed for profile: {}", acmeProfileName);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.MALFORMED);
        }

        // Parse and check the JWS request
        AcmeJwsRequest jwsRequest = new AcmeJwsRequest(requestJson);

        CertificateRevocationRequest request = AcmeJsonProcessor.getPayloadAsRequestObject(jwsRequest.getJwsObject(), CertificateRevocationRequest.class);
        logger.debug("Certificate revocation is triggered with the payload: {}", request.toString());

        X509Certificate x509Certificate = (X509Certificate) CertificateFactory.getInstance(AcmeConstants.CERTIFICATE_TYPE)
                .generateCertificate(new ByteArrayInputStream(Base64.getUrlDecoder().decode(request.getCertificate())));

        String decodedCertificate = X509ObjectToString.toPem(x509Certificate)
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("-----END CERTIFICATE-----", "");

        ClientCertificateRevocationDto revokeRequest = new ClientCertificateRevocationDto();

        Certificate cert = certificateService.getCertificateEntityByContent(decodedCertificate);
        if (cert.getState().equals(CertificateState.REVOKED)) {
            logger.error("Certificate is already revoked. Serial number: {}, Fingerprint: {}", cert.getSerialNumber(), cert.getFingerprint());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ALREADY_REVOKED);
        }

        String pemPubKeyJws = "";
        if (jwsRequest.getJwsObject().getHeader().toJSONObject().containsKey("jwk")) {
            pemPubKeyJws = AcmePublicKeyProcessor.publicKeyPemStringFromObject(jwsRequest.getPublicKey());
        }

        PublicKey accountPublicKey;
        PublicKey certPublicKey;
        AcmeAccount account = null;
        String accountKid = jwsRequest.getJwsObject().getHeader().toJSONObject().get("kid").toString();
        logger.debug("kid of the Account for revocation: {}", accountKid);
        if (jwsRequest.getJwsObject().getHeader().toJSONObject().containsKey("kid")) {
            String accountId = accountKid.split("/")[accountKid.split("/").length - 1];
            account = getAcmeAccountEntity(accountId);
            validateAccount(account);
            try {
                accountPublicKey = AcmePublicKeyProcessor.publicKeyObjectFromString(account.getPublicKey());
            } catch (Exception e) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }
        } else {
            accountPublicKey = jwsRequest.getPublicKey();
        }
        certPublicKey = x509Certificate.getPublicKey();
        if (jwsRequest.getJwsObject().getHeader().toJSONObject().containsKey("jwk")) {
            String pemPubKeyCert = AcmePublicKeyProcessor.publicKeyPemStringFromObject(certPublicKey);
            String pemPubKeyAcc = AcmePublicKeyProcessor.publicKeyPemStringFromObject(accountPublicKey);
            if (!pemPubKeyCert.equals(pemPubKeyJws) || pemPubKeyAcc.equals(pemPubKeyJws)) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
            }
        }

        if ((accountPublicKey != null && jwsRequest.checkSignature(accountPublicKey))) {
            logger.info("ACME Revocation request is signed by Account key: {}", request);
        } else if ((certPublicKey != null && jwsRequest.checkSignature(certPublicKey))) {
            logger.info("ACME Revocation request is signed by private key associated to the Certificate: {}", request);
        } else {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_PUBLIC_KEY);
        }

        // if the revocation reason is null, set it to UNSPECIFIED, otherwise get the code from the request
        final CertificateRevocationReason reason = request.getReason() == null ? CertificateRevocationReason.UNSPECIFIED : CertificateRevocationReason.fromReasonCode(request.getReason());
        // when the reason is null, it means, that is not in the list
        if (reason == null) {
            final String details = "Allowed revocation reason codes are: " + Arrays.toString(Arrays.stream(CertificateRevocationReason.values()).map(CertificateRevocationReason::getCode).toArray());
            throw new AcmeProblemDocumentException(HttpStatus.FORBIDDEN, Problem.BAD_REVOCATION_REASON, details);
        }
        revokeRequest.setReason(reason);
        revokeRequest.setAttributes(getClientOperationAttributes(true, account));

        try {
            clientOperationService.revokeCertificate(SecuredParentUUID.fromUUID(cert.getRaProfile().getAuthorityInstanceReferenceUuid()), cert.getRaProfile().getSecuredUuid(), cert.getUuid().toString(), revokeRequest);
            return ResponseEntity
                    .ok()
                    .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                    .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                    .build();
        } catch (NotFoundException e) {
            return ResponseEntity
                    .badRequest()
                    .header(AcmeConstants.NONCE_HEADER_NAME, generateNonce())
                    .header(AcmeConstants.LINK_HEADER_NAME, generateLinkHeader(acmeProfileName))
                    .build();
        }
    }

    private boolean isRaProfileBased() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/");
    }

    private DirectoryMeta frameDirectoryMeta(String profileName) throws NotFoundException {
        AcmeProfile acmeProfile;
        if (isRaProfileBased()) {
            acmeProfile = getRaProfileEntity(profileName).getAcmeProfile();
        } else {
            acmeProfile = acmeProfileRepository.findByName(profileName).orElse(null);
        }
        if (acmeProfile == null) {
            throw new NotFoundException(AcmeProfile.class, profileName);
        }
        DirectoryMeta meta = new DirectoryMeta();
        meta.setCaaIdentities(new String[0]);
        meta.setTermsOfService(acmeProfile.getTermsOfServiceUrl());
        meta.setExternalAccountRequired(false);
        meta.setWebsite(acmeProfile.getWebsite());
        logger.debug("Directory meta: {}", meta);
        return meta;
    }

    private String generateNonce() {
        String nonceString = AcmeRandomGeneratorAndValidator.generateNonce();
        Date expires = AcmeCommonHelper.addSeconds(new Date(), AcmeConstants.NONCE_VALIDITY);
        AcmeNonce acmeNonce = new AcmeNonce();
        acmeNonce.setCreated(new Date());
        acmeNonce.setNonce(nonceString);
        acmeNonce.setExpires(expires);
        acmeNonceRepository.save(acmeNonce);
        return nonceString;
    }

    // RFC 8555 Section 7.1
    // The "index" link relation is present on all resources other than the
    // directory and indicates the URL of the directory.
    private String generateLinkHeader(String profileName) {
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return "<" + baseUri + AcmeConstants.ACME_URI_HEADER + "/" + profileName + "/directory>;rel=\"index\"";
    }

    private AcmeAccount addNewAccount(String profileName, String publicKey, NewAccountRequest accountRequest) throws AcmeProblemDocumentException {
        AcmeProfile acmeProfile;
        RaProfile raProfileToUse;

        if (isRaProfileBased()) {
            try {
                raProfileToUse = getRaProfileEntity(profileName);
            } catch (NotFoundException e) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("profileNotFound", "Profile Not Found", "Given profile name is not found"));
            }
            acmeProfile = raProfileToUse.getAcmeProfile();
        } else {
            try {
                acmeProfile = getAcmeProfileEntityByName(profileName);
            } catch (NotFoundException e) {
                throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("profileNotFound", "ACME Profile Not Found", "ACME Profile is not found"));
            }
            raProfileToUse = acmeProfile.getRaProfile();
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

        if (!isRaProfileBased() && acmeProfile.getRaProfile() == null) {
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

    private RaProfile getRaProfileEntity(String name) throws NotFoundException {
        return raProfileRepository.findByName(name).orElseThrow(() -> new NotFoundException(RaProfile.class, name));
    }

    private AcmeProfile getAcmeProfileEntityByName(String name) throws NotFoundException {
        return acmeProfileRepository.findByName(name).orElseThrow(() -> new NotFoundException(AcmeProfile.class, name));
    }

    private AcmeAccount getAcmeAccountEntity(String accountId) throws NotFoundException {
        return acmeAccountRepository.findByAccountId(accountId).orElseThrow(() -> new NotFoundException(AcmeAccount.class, accountId));
    }

    private void validateAccount(AcmeAccount acmeAccount) throws AcmeProblemDocumentException {
        if (!acmeAccount.getStatus().equals(AccountStatus.VALID)) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("accountDeactivated",
                    "Account Deactivated",
                    "The requested account has been deactivated"));
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

    // TODO: Clarify what is validated here
    private void validateKey(JWSObject jwsRequestObject, JWSObject jwsInnerObject) throws AcmeProblemDocumentException {
        if (!jwsInnerObject.getHeader().toJSONObject().containsKey("jwk")) {
            throw new AcmeProblemDocumentException(
                    HttpStatus.BAD_REQUEST,
                    new ProblemDocument("malformed", "Inner JWS Malformed", "Inner JWS does not contain jwk"));
        }
        if (!jwsInnerObject.getHeader().toJSONObject().getOrDefault("url", "innerUrl")
                .equals(jwsRequestObject.getHeader().toJSONObject().getOrDefault("url", "outerUrl"))) {
            throw new AcmeProblemDocumentException(
                    HttpStatus.BAD_REQUEST,
                    new ProblemDocument("malformed", "Inner JWS Malformed", "URL in inner and outer JWS are different"));
        }
        if (jwsInnerObject.getHeader().toJSONObject().containsKey("nonce")) {
            throw new AcmeProblemDocumentException(
                    HttpStatus.BAD_REQUEST,
                    new ProblemDocument("malformed", "Inner JWS Malformed", "Inner JWS cannot contain nonce header"));
        }
    }

    private AcmeOrder generateOrder(AcmeAccount acmeAccount, AcmeJwsRequest jwsRequest) {
        logger.debug("Generating new Order for Account: {}", acmeAccount.toString());
        Order orderRequest = AcmeJsonProcessor.getPayloadAsRequestObject(jwsRequest.getJwsObject(), Order.class);
        logger.debug("Order requested: {}", orderRequest.toString());
        AcmeOrder order = new AcmeOrder();
        order.setAcmeAccount(acmeAccount);
        order.setOrderId(AcmeRandomGeneratorAndValidator.generateRandomId());
        order.setStatus(OrderStatus.PENDING);
        order.setNotAfter(AcmeCommonHelper.getDateFromString(orderRequest.getNotAfter()));
        order.setNotBefore(AcmeCommonHelper.getDateFromString(orderRequest.getNotBefore()));
        order.setIdentifiers(SerializationUtil.serializeIdentifiers(orderRequest.getIdentifiers()));
        if (acmeAccount.getAcmeProfile().getValidity() != null) {
            order.setExpires(AcmeCommonHelper.addSeconds(new Date(), acmeAccount.getAcmeProfile().getValidity()));
        } else {
            order.setExpires(AcmeCommonHelper.getDefaultExpires());
        }
        acmeOrderRepository.save(order);
        logger.debug("Order created: {}", order);
        Set<AcmeAuthorization> authorizations = generateValidations(order, orderRequest.getIdentifiers(), jwsRequest.getPublicKey());
        order.setAuthorizations(authorizations);
        logger.debug("Challenges created for Order: {}", order);
        return order;
    }

    private Set<AcmeAuthorization> generateValidations(AcmeOrder acmeOrder, List<Identifier> identifiers, PublicKey publicKey) {
        Set<AcmeAuthorization> authorizations = new HashSet<>();
        for (Identifier identifier : identifiers) {
            authorizations.add(authorization(acmeOrder, identifier, publicKey));
        }
        return authorizations;
    }

    private AcmeAuthorization authorization(AcmeOrder acmeOrder, Identifier identifier, PublicKey publicKey) {
        AcmeAuthorization authorization = new AcmeAuthorization();
        authorization.setAuthorizationId(AcmeRandomGeneratorAndValidator.generateRandomId());
        authorization.setStatus(AuthorizationStatus.PENDING);
        authorization.setOrder(acmeOrder);
        if (acmeOrder.getAcmeAccount().getAcmeProfile().getValidity() != null) {
            authorization.setExpires(AcmeCommonHelper.addSeconds(new Date(), acmeOrder.getAcmeAccount().getAcmeProfile().getValidity()));
        } else {
            authorization.setExpires(AcmeCommonHelper.getDefaultExpires());
        }
        authorization.setWildcard(checkWildcard(identifier));
        authorization.setIdentifier(SerializationUtil.serialize(identifier));
        acmeAuthorizationRepository.save(authorization);
        AcmeChallenge dnsChallenge = generateChallenge(ChallengeType.DNS01, authorization, publicKey);
        AcmeChallenge httpChallenge = generateChallenge(ChallengeType.HTTP01, authorization, publicKey);
        authorization.setChallenges(Set.of(dnsChallenge, httpChallenge));
        return authorization;
    }

    private boolean checkWildcard(Identifier identifier) {
        return identifier.getValue().contains("*");
    }

    private AcmeChallenge generateChallenge(ChallengeType challengeType, AcmeAuthorization authorization, PublicKey publicKey) {
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

    private void updateOrderStatusForAccount(AcmeAccount account) {
        List<AcmeOrder> orders = acmeOrderRepository.findByAcmeAccountAndExpiresBefore(account, new Date());
        for (AcmeOrder order : orders) {
            if (!order.getStatus().equals(OrderStatus.VALID)) {
                order.setStatus(OrderStatus.INVALID);
                acmeOrderRepository.save(order);
            }
        }
    }

    private boolean validateHttpChallenge(AcmeChallenge challenge) throws AcmeProblemDocumentException {
        logger.debug("Initiating HTTP-01 Challenge validation: {}", challenge.toString());
        String response = getHttpChallengeResponse(
                SerializationUtil.deserializeIdentifier(
                                challenge
                                        .getAuthorization()
                                        .getIdentifier()
                        )
                        .getValue().replace("*.", ""),
                challenge.getToken());
        PublicKey pubKey;
        try {
            pubKey = AcmePublicKeyProcessor.publicKeyObjectFromString(challenge.getAuthorization().getOrder().getAcmeAccount().getPublicKey());
        } catch (Exception e) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.SERVER_INTERNAL);
        }
        String expectedResponse = AcmeCommonHelper.createKeyAuthorization(challenge.getToken(), pubKey);
        logger.debug("HTTP01 validation response from the server: {}, expected response: {}", response, expectedResponse);
        return response.equals(expectedResponse);
    }

    private boolean validateDnsChallenge(AcmeChallenge challenge) throws AcmeProblemDocumentException {
        logger.info("Initiating DNS-01 validation for challenge: {}", challenge.toString());
        Properties env = getEnv(challenge);
        List<String> txtRecords = new ArrayList<>();
        String expectedKeyAuthorization = generateDnsValidationToken(challenge.getAuthorization().getOrder().getAcmeAccount().getPublicKey(), challenge.getToken());
        DirContext context;
        try {
            context = new InitialDirContext(env);
            Attributes list = context.getAttributes(AcmeConstants.DNS_ACME_PREFIX
                            + SerializationUtil.deserializeIdentifier(
                            challenge.getAuthorization().getIdentifier()).getValue(),
                    new String[]{AcmeConstants.DNS_RECORD_TYPE});
            NamingEnumeration<? extends Attribute> records = list.getAll();

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

    private static Properties getEnv(AcmeChallenge challenge) {
        Properties env = new Properties();
        env.setProperty(Context.INITIAL_CONTEXT_FACTORY, AcmeConstants.DNS_CONTENT_FACTORY);
        AcmeProfile acmeProfile = challenge.getAuthorization().getOrder().getAcmeAccount().getAcmeProfile();
        if (acmeProfile.getDnsResolverIp() == null || acmeProfile.getDnsResolverIp().isEmpty()) {
            env.setProperty(Context.PROVIDER_URL, AcmeConstants.DNS_ENV_PREFIX);
        } else {
            env.setProperty(Context.PROVIDER_URL, AcmeConstants.DNS_ENV_PREFIX + acmeProfile.getDnsResolverIp() + ":" + Optional.ofNullable(acmeProfile.getDnsResolverPort())
                    .orElse(AcmeConstants.DEFAULT_DNS_PORT));
        }
        return env;
    }

    private String getHttpChallengeResponse(String domain, String token) throws AcmeProblemDocumentException {
        return getResponseFollowRedirects(String.format(AcmeConstants.HTTP_CHALLENGE_BASE_URL, domain, token));
    }

    private String getResponseFollowRedirects(String url) throws AcmeProblemDocumentException {
        String finalUrl = url;
        String acmeChallengeOutput = "";
        int redirectFollowCount = 0;
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
                connection.setRequestMethod(AcmeConstants.HTTP_CHALLENGE_REQUEST_METHOD);
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    acmeChallengeOutput = bufferedReader.lines().collect(Collectors.joining());
                }
                if (responseCode >= 300 && responseCode < 400) {
                    String redirectedUrl = connection.getHeaderField(AcmeConstants.LOCATION_HEADER_NAME);
                    if (null == redirectedUrl) {
                        break;
                    }
                    finalUrl = redirectedUrl;
                } else
                    break;
            } while (connection.getResponseCode() != HttpURLConnection.HTTP_OK && redirectFollowCount < AcmeConstants.MAX_REDIRECT_COUNT);
            connection.disconnect();
        } catch (AcmeProblemDocumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return acmeChallengeOutput;
    }

    private String generateDnsValidationToken(String publicKey, String token) throws AcmeProblemDocumentException {
        MessageDigest digest;
        try {
            PublicKey pubKey = AcmePublicKeyProcessor.publicKeyObjectFromString(publicKey);
            digest = MessageDigest.getInstance(AcmeConstants.MESSAGE_DIGEST_ALGORITHM);
            final byte[] encodedHashOfExpectedKeyAuthorization = digest.digest(AcmeCommonHelper.createKeyAuthorization(token, pubKey).getBytes(StandardCharsets.UTF_8));
            return Base64URL.encode(encodedHashOfExpectedKeyAuthorization).toString();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error(e.getMessage());
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.SERVER_INTERNAL);
        }
    }

    private void validateCSR(JcaPKCS10CertificationRequest csr, AcmeOrder order) throws AcmeProblemDocumentException {
        List<String> sans = new ArrayList<>();
        List<String> dnsIdentifiers = new ArrayList<>();

        org.bouncycastle.asn1.pkcs.Attribute[] certAttributes = csr.getAttributes();
        try {
            String commonName = IETFUtils.valueToString(csr.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue());
            if (!commonName.isEmpty()) {
                sans.add(commonName);
                dnsIdentifiers.add(commonName);
            }

        } catch (Exception e) {
            logger.warn("Unable to find common name: {}", e.getMessage());
        }
        for (org.bouncycastle.asn1.pkcs.Attribute attribute : certAttributes) {
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
                .toList();

        List<String> identifiersDns = new ArrayList<>();
        for (Identifier identifier : SerializationUtil.deserializeIdentifiers(order.getIdentifiers())) {
            if (identifier.getType().equals("dns")) {
                identifiersDns.add(identifier.getValue());
            }
        }

        if (!new HashSet<>(sans).containsAll(identifiers)) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_CSR);
        }
        if (!new HashSet<>(dnsIdentifiers).containsAll(identifiersDns)) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_CSR);
        }
    }

    private String JcaPKCS10CertificationRequestToString(JcaPKCS10CertificationRequest csr) throws IOException {
        PemObject pemCSR = new PemObject("CERTIFICATE REQUEST", csr.getEncoded());
        StringWriter decodedCsr = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(decodedCsr);
        pemWriter.writeObject(pemCSR);
        pemWriter.close();
        decodedCsr.close();
        return decodedCsr.toString();
    }

    private List<RequestAttributeDto> getClientOperationAttributes(boolean isRevoke, AcmeAccount acmeAccount) {
        if (acmeAccount == null) {
            return List.of();
        }
        String attributes;
        if (ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/")) {
            if (isRevoke) {
                attributes = acmeAccount.getRaProfile().getProtocolAttribute().getAcmeRevokeCertificateAttributes();
            } else {
                attributes = acmeAccount.getRaProfile().getProtocolAttribute().getAcmeIssueCertificateAttributes();
            }
        } else {
            if (isRevoke) {
                attributes = acmeAccount.getAcmeProfile().getRevokeCertificateAttributes();
            } else {
                attributes = acmeAccount.getAcmeProfile().getIssueCertificateAttributes();
            }
        }
        return AttributeDefinitionUtils.getClientAttributes(AttributeDefinitionUtils.deserialize(attributes, DataAttribute.class));
    }

    private void createCert(AcmeOrder order, ClientCertificateSignRequestDto certificateSignRequestDto) {
        // check if certificate is not already requested (prevent calling finalize multiple times issuing more certificates)
        // not sure if it is necessary
        if (order.getCertificateReference() == null) {
            try {
                // keep state as PROCESSING since issuing is async process
                if (logger.isDebugEnabled()) {
                    logger.debug("Requesting Certificate for the Order: {} and certificate signing request: {}", order, certificateSignRequestDto);
                }
                ClientCertificateDataResponseDto certificateOutput = clientOperationService.issueCertificate(SecuredParentUUID.fromUUID(order.getAcmeAccount().getRaProfile().getAuthorityInstanceReferenceUuid()), order.getAcmeAccount().getRaProfile().getSecuredUuid(), certificateSignRequestDto);
                order.setCertificateId(AcmeRandomGeneratorAndValidator.generateRandomId());
                order.setCertificateReference(certificateService.getCertificateEntity(SecuredUUID.fromString(certificateOutput.getUuid())));
            } catch (Exception e) {
                logger.error("Issue Certificate failed. Exception: {}", e.getMessage());
                order.setStatus(OrderStatus.INVALID);
            }
            acmeOrderRepository.save(order);
        } else {
            OrderStatus newStatus = checkOrderStatusByCertificate(order.getCertificateReference());
            logger.debug("Calling finalize of Order but certificate is already requested. Current status: {}", newStatus);
            if(!newStatus.equals(order.getStatus())) {
                order.setStatus(newStatus);
                acmeOrderRepository.save(order);
            }
        }
    }

    private OrderStatus checkOrderStatusByCertificate(Certificate certificate) {
        if (certificate.getState().equals(CertificateState.ISSUED)) return OrderStatus.VALID;
        if (certificate.getState().equals(CertificateState.REQUESTED)
                || certificate.getState().equals(CertificateState.PENDING_APPROVAL)
                || certificate.getState().equals(CertificateState.PENDING_ISSUE)) return OrderStatus.PROCESSING;

        return OrderStatus.INVALID;
    }

    protected AcmeOrder getAcmeOrderEntity(String orderId) throws AcmeProblemDocumentException {
        logger.info("Gathering ACME Order details with ID: {}", orderId);
        AcmeOrder acmeOrder = acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("orderNotFound", "Order Not Found", "Specified ACME Order not found")));
        logger.debug("Order: {}", acmeOrder);

        // check for status if certificate reference is set
        if(acmeOrder.getCertificateReference() != null) {
            OrderStatus newStatus = checkOrderStatusByCertificate(acmeOrder.getCertificateReference());
            if (!newStatus.equals(acmeOrder.getStatus())) {
                logger.info("ACME Order status changed from {} to {}.", acmeOrder.getStatus(), newStatus);
                acmeOrder.setStatus(newStatus);
                acmeOrderRepository.save(acmeOrder);
            }
        }

        return acmeOrder;
    }

    private void updateOrderStatusByExpiry(AcmeOrder order) {
        if (order.getExpires() != null && order.getExpires().before(new Date()) && !order.getStatus().equals(OrderStatus.VALID)) {
            order.setStatus(OrderStatus.INVALID);
            acmeOrderRepository.save(order);
        }
    }

    protected ByteArrayResource getCertificateResource(String certificateId) throws NotFoundException, CertificateException {
        AcmeOrder order = acmeOrderRepository.findByCertificateId(certificateId).orElseThrow(() -> new NotFoundException(Order.class, certificateId));
        CertificateChainResponseDto certificateChainResponse = certificateService.getCertificateChain(SecuredUUID.fromUUID(order.getCertificateReferenceUuid()), true);
        String chainString = frameCertChainString(certificateChainResponse.getCertificates());
        return new ByteArrayResource(chainString.getBytes(StandardCharsets.UTF_8));
    }

    protected String frameCertChainString(List<CertificateDetailDto> certificates) throws CertificateException {
        List<String> chain = new ArrayList<>();
        for (CertificateDetailDto certificate : certificates) {
            chain.add(X509ObjectToString.toPem(getX509(certificate.getCertificateContent())));
        }
        return String.join("\r\n", chain);
    }

    private X509Certificate getX509(String certificate) throws CertificateException {
        return CertificateUtil.getX509Certificate(certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("-----END CERTIFICATE-----", ""));
    }
}
