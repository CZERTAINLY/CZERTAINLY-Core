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
import com.czertainly.api.model.core.acme.ProblemDocument;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.acme.AcmeRaProfileService;
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
public class AcmeRaProfileServiceImpl implements AcmeRaProfileService {

    private static final String NONCE_HEADER_NAME = "Replay-Nonce";
    private static final String RETRY_HEADER_NAME = "Retry-After";

    private static final Logger logger = LoggerFactory.getLogger(AcmeRaProfileServiceImpl.class);

    @Autowired
    private ExtendedAcmeHelperService extendedAcmeHelperService;
    @Autowired
    private AcmeAccountRepository acmeAccountRepository;
    @Autowired
    private AcmeChallengeRepository acmeChallengeRepository;
    @Autowired
    private AcmeAuthorizationRepository acmeAuthorizationRepository;
    @Autowired
    private AcmeOrderRepository acmeOrderRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AcmeProfileRepository acmeProfileRepository;

    @Override
    public ResponseEntity<Directory> getDirectory(String raProfileName) throws AcmeProblemDocumentException {
        logger.info("Gathering Directory information for RA Profile ACME: {}", raProfileName);
        Directory directory = extendedAcmeHelperService.frameDirectory(raProfileName);
        logger.debug("Directory information retrieved: {}", directory.toString());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME,
                extendedAcmeHelperService.generateNonce()).body(directory);
    }

    @Override
    public ResponseEntity<?> getNonce(Boolean isHead) {
        String nonce = extendedAcmeHelperService.generateNonce();
        logger.debug("New Nonce: {}", nonce);

        if(isHead){
            ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME,
                    nonce).build();
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME,
                nonce).build();
    }

    @Override
    public ResponseEntity<Account> newAccount(String raProfileName, String requestJson) throws AcmeProblemDocumentException {
        return extendedAcmeHelperService.processNewAccount(raProfileName, requestJson);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String raProfileName, String accountId, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        extendedAcmeHelperService.initialize(requestJson);
        return extendedAcmeHelperService.updateAccount(accountId);
    }

    @Override
    public ResponseEntity<?> keyRollover(String raProfileName, String jwsBody) throws AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        return extendedAcmeHelperService.keyRollover();

    }

    @Override
    public ResponseEntity<Order> newOrder(String raProfileName, String requestJson) throws AcmeProblemDocumentException {
        return extendedAcmeHelperService.processNewOrder(getAcmeProfileName(raProfileName), requestJson);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String raProfileName, String accountId) throws AcmeProblemDocumentException {
        AcmeAccount acmeAccount = extendedAcmeHelperService.getAcmeAccountEntity(accountId);
        extendedAcmeHelperService.updateOrderStatusForAccount(acmeAccount);
        return extendedAcmeHelperService.listOrders(accountId);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorization(String raProfileName, String authorizationId, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        Authorization authorization = extendedAcmeHelperService.checkDeactivateAuthorization(authorizationId);
        logger.debug("New Authorization: {}", authorization.toString());
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .body(authorization);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String raProfileName, String challengeId) throws AcmeProblemDocumentException {
        logger.info("Validating Challenge with ID: {}", challengeId);
        AcmeChallenge challenge = extendedAcmeHelperService.validateChallenge(challengeId);
        logger.debug("Challenge: {}", challenge.toString());
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .header("Link", "<"+challenge.getAuthorization().getUrl() + ">;rel=\"up\"")
                .body(challenge.mapToDto());
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String raProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, ConnectorException {
        elevatePermission();
        extendedAcmeHelperService.initialize(jwsBody);
        AcmeOrder order = extendedAcmeHelperService.checkOrderForFinalize(orderId);
        logger.debug("Finalizing the Order with ID: {}", orderId);
        extendedAcmeHelperService.finalizeOrder(order);
        order.setStatus(OrderStatus.PROCESSING);
        return ResponseEntity
                .ok()
                .location(URI.create(order.getUrl()))
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .header(RETRY_HEADER_NAME, order.getAcmeAccount().getAcmeProfile().getRetryInterval().toString())
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Order> getOrder(String raProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException {
        logger.info("Get Order details with ID: {}", orderId);
        AcmeOrder order = extendedAcmeHelperService.getAcmeOrderEntity(orderId);
        logger.debug("Order: {}", order);
        extendedAcmeHelperService.updateOrderStatusByExpiry(order);
        if(order.getStatus().equals(OrderStatus.INVALID)){
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
    public ResponseEntity<Resource> downloadCertificate(String raProfileName, String certificateId) throws NotFoundException, CertificateException {
        elevatePermission();
        logger.info("Downloading Certificate with ID: {}", certificateId);
        ByteArrayResource byteArrayResource = extendedAcmeHelperService.getCertificateResource(certificateId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, extendedAcmeHelperService.generateNonce())
                .contentType(MediaType.valueOf("application/pem-certificate-chain"))
                .body(byteArrayResource);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String raProfileName, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, CertificateException {
        elevatePermission();
        extendedAcmeHelperService.initialize(jwsBody);
        return extendedAcmeHelperService.revokeCertificate();
    }

    private void elevatePermission(){
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ACME"));
        Authentication reAuth = new UsernamePasswordAuthenticationToken("ACME_USER","",authorities);
        SecurityContextHolder.getContext().setAuthentication(reAuth);
        SecurityContextHolder.getContext().getAuthentication();
    }

    private String getAcmeProfileName(String raProfileName) throws AcmeProblemDocumentException{
        RaProfile raProfile = raProfileRepository.findByName(raProfileName).orElseThrow(() ->
                new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                        new ProblemDocument("invalidRaProfile",
                                "RA Profile Not Found",
                                "RA Profile is not found")));
        if(raProfile.getAcmeProfile() == null){
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileNotTagged",
                            "ACME not activated",
                            "ACME is not activated for the given RA Profile"));
        }
        return raProfile.getAcmeProfile().getName();
    }

}
