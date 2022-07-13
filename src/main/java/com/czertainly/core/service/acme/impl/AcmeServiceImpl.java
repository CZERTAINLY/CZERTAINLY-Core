package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.Account;
import com.czertainly.api.model.core.acme.Authorization;
import com.czertainly.api.model.core.acme.Challenge;
import com.czertainly.api.model.core.acme.Directory;
import com.czertainly.api.model.core.acme.Order;
import com.czertainly.api.model.core.acme.OrderStatus;
import com.czertainly.api.model.core.acme.Problem;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.service.acme.AcmeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class AcmeServiceImpl implements AcmeService {

    private static final String NONCE_HEADER_NAME = "Replay-Nonce";
    private static final String RETRY_HEADER_NAME = "Retry-After";

    private static final Logger logger = LoggerFactory.getLogger(AcmeServiceImpl.class);

    @Autowired
    private ExtendedAcmeHelperService extendedAcmeHelperService;

    @Override
    public ResponseEntity<Directory> getDirectory(String acmeProfileName) throws AcmeProblemDocumentException {
        logger.info("Gathering Directory information for ACME: {}", acmeProfileName);
        Directory directory = extendedAcmeHelperService.frameDirectory(acmeProfileName);
        logger.debug("Directory information retrieved: {}", directory.toString());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME,
                extendedAcmeHelperService.generateNonce()).body(directory);
    }

    @Override
    public ResponseEntity<?> getNonce(Boolean isHead) {
        String nonce = extendedAcmeHelperService.generateNonce();
        logger.debug("New Nonce: {}", nonce);
        if (isHead) {
            ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME,
                    nonce).build();
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME,
                nonce).build();
    }

    @Override
    public ResponseEntity<Account> newAccount(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        return extendedAcmeHelperService.processNewAccount(acmeProfileName, requestJson);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String acmeProfileName, String accountId, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        extendedAcmeHelperService.initialize(requestJson);
        return extendedAcmeHelperService.updateAccount(accountId);
    }

    @Override
    public ResponseEntity<?> keyRollover(String acmeProfileName, String jwsBody) throws AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        return extendedAcmeHelperService.keyRollover();

    }

    @Override
    public ResponseEntity<Order> newOrder(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        return extendedAcmeHelperService.processNewOrder(acmeProfileName, requestJson);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String acmeProfileName, String accountId) throws AcmeProblemDocumentException {
        AcmeAccount acmeAccount = extendedAcmeHelperService.getAcmeAccountEntity(accountId);
        extendedAcmeHelperService.updateOrderStatusForAccount(acmeAccount);
        return extendedAcmeHelperService.listOrders(accountId);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorization(String acmeProfileName, String authorizationId, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        Authorization authorization = extendedAcmeHelperService.checkDeactivateAuthorization(authorizationId);
        logger.debug("Authorization: {}", authorization.toString());
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .body(authorization);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId) throws AcmeProblemDocumentException {
        if (logger.isDebugEnabled()) {
            logger.debug("Validating Challenge with ID {}:", challengeId);
        }
        AcmeChallenge challenge = extendedAcmeHelperService.validateChallenge(challengeId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .header("Link", "<" + challenge.getAuthorization().getUrl() + ">;rel=\"up\"")
                .body(challenge.mapToDto());
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String acmeProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        AcmeOrder order = extendedAcmeHelperService.checkOrderForFinalize(orderId);
        logger.debug("Finalizing Order with ID: {}", orderId);
        extendedAcmeHelperService.finalizeOrder(order);
        order.setStatus(OrderStatus.PROCESSING);
        return ResponseEntity
                .ok()
                .location(URI.create(order.getUrl()))
                .header("Retry-After")
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .header(RETRY_HEADER_NAME, order.getAcmeAccount().getAcmeProfile().getRetryInterval().toString())
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Order> getOrder(String acmeProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException {
        logger.info("Get Order details with ID: {}.", orderId);
        AcmeOrder order = extendedAcmeHelperService.getAcmeOrderEntity(orderId);
        logger.debug("Order details: {}", order.toString());
        extendedAcmeHelperService.updateOrderStatusByExpiry(order);
        if (order.getStatus().equals(OrderStatus.INVALID)) {
            logger.error("Order status is invalid: {}", order);
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.SERVER_INTERNAL);
        }
        return ResponseEntity
                .ok()
                .location(URI.create(order.getUrl()))
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .header(RETRY_HEADER_NAME, order.getAcmeAccount().getAcmeProfile().getRetryInterval().toString())
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws NotFoundException, CertificateException {
        logger.info("Downloading the Certificate with ID: {}", certificateId);
        ByteArrayResource byteArrayResource = extendedAcmeHelperService.getCertificateResource(certificateId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .contentType(MediaType.valueOf("application/pem-certificate-chain"))
                .body(byteArrayResource);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String acmeProfileName, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, CertificateException {
        extendedAcmeHelperService.initialize(jwsBody);
        return extendedAcmeHelperService.revokeCertificate();
    }
}
