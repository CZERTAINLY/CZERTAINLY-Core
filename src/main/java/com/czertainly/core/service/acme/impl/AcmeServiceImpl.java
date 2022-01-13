package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeAuthorization;
import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.acme.AcmeService;
import com.czertainly.core.util.AcmePublicKeyProcessor;
import com.czertainly.core.util.AcmeRandomGeneratorAndValidator;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.X509ObjectToString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.transaction.Transactional;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

/**
 * Acme service implementation class containing the implementation logic for
 * acme implementations
 */
@Service
@Transactional
public class AcmeServiceImpl implements AcmeService {

    // Nonce Check
    // Url request and inside JWS check
    private static final String NONCE_HEADER_NAME = "Replay-Nonce";

    private static final Logger logger = LoggerFactory.getLogger(AcmeServiceImpl.class);

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

    @Override
    public Directory getDirectory(String acmeProfileName) {
        return extendedAcmeHelperService.frameDirectory(acmeProfileName);
    }

    @Override
    public ResponseEntity<Account> newAccount(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        return extendedAcmeHelperService.processNewAccount(acmeProfileName, requestJson);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String acmeProfileName, String accountId, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        extendedAcmeHelperService.initialize(requestJson);
        return extendedAcmeHelperService.updateAccount(accountId);
    }

    @Override
    public ResponseEntity<?> keyRollover(String acmeProfileName, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        return extendedAcmeHelperService.keyRollover();

    }

    @Override
    public ResponseEntity<Order> newOrder(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        return extendedAcmeHelperService.processNewOrder(acmeProfileName, requestJson);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String acmeProfileName, String accountId) throws NotFoundException {
        return extendedAcmeHelperService.listOrders(accountId);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorization(String acmeProfileName, String authorizationId, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        Authorization authorization = extendedAcmeHelperService.checkDeactivateAuthorization(authorizationId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(authorization);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException {
        AcmeChallenge challenge = extendedAcmeHelperService.validateChallenge(challengeId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .header("Link", challenge.getAuthorization().getUrl() + ">;rel=\"up\"")
                .body(challenge.mapToDto());
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String acmeProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException {
        elevatePermission();
        extendedAcmeHelperService.initialize(jwsBody);
        AcmeOrder order = extendedAcmeHelperService.finalizeOrder(orderId);
        return ResponseEntity
                .ok()
                .location(URI.create(order.getUrl()))
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Order> getOrder(String acmeProfileName, String orderId) throws NotFoundException {
        AcmeOrder order = extendedAcmeHelperService.getAcmeOrderEntity(orderId);
        return ResponseEntity
                .ok()
                .location(URI.create(order.getUrl()))
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws NotFoundException, CertificateException {
        elevatePermission();
        ByteArrayResource byteArrayResource = extendedAcmeHelperService.getCertificateResource(certificateId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .contentType(MediaType.valueOf("application/pem-certificate-chain"))
                .body(byteArrayResource);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String acmeProfileName, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, CertificateException {
        elevatePermission();
        extendedAcmeHelperService.initialize(jwsBody);
        return extendedAcmeHelperService.revokeCertificate();
    }

    private void elevatePermission(){
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("SUPERADMINISTRATOR"));
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPERADMINISTRATOR"));
        Authentication reAuth = new UsernamePasswordAuthenticationToken(SecurityContextHolder.getContext().getAuthentication().getPrincipal(),"",authorities);
        SecurityContextHolder.getContext().setAuthentication(reAuth);
        Authentication role = SecurityContextHolder.getContext().getAuthentication();
    }

}
