package com.czertainly.core.service.acme.impl;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.acme.RaProfileBasedAcmeController;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.acme.AcmeService;
import com.czertainly.core.service.acme.RaProfileBasedAcmeService;
import com.czertainly.core.util.AcmeRandomGeneratorAndValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Acme service implementation class containing the implementation logic for
 * acme implementations
 */
@Service
@Transactional
public class RaProfileBasedAcmeServiceImpl implements RaProfileBasedAcmeService {

    // Nonce Check
    // Url request and inside JWS check
    private static final String NONCE_HEADER_NAME = "Replay-Nonce";

    private static final Logger logger = LoggerFactory.getLogger(RaProfileBasedAcmeServiceImpl.class);

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
    public Directory getDirectory(String raProfileName) throws NotFoundException {
        return extendedAcmeHelperService.frameDirectory(raProfileName);
    }

    @Override
    public ResponseEntity<Account> newAccount(String raProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        return extendedAcmeHelperService.processNewAccount(raProfileName, requestJson);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String raProfileName, String accountId, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        extendedAcmeHelperService.initialize(requestJson);
        return extendedAcmeHelperService.updateAccount(accountId);
    }

    @Override
    public ResponseEntity<?> keyRollover(String raProfileName, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        return extendedAcmeHelperService.keyRollover();

    }

    @Override
    public ResponseEntity<Order> newOrder(String raProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        return extendedAcmeHelperService.processNewOrder(getAcmeProfileName(raProfileName), requestJson);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String raProfileName, String accountId) throws NotFoundException {
        return extendedAcmeHelperService.listOrders(accountId);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorization(String raProfileName, String authorizationId, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(jwsBody);
        Authorization authorization = extendedAcmeHelperService.checkDeactivateAuthorization(authorizationId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(authorization);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String raProfileName, String challengeId) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException {
        AcmeChallenge challenge = extendedAcmeHelperService.validateChallenge(challengeId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .header("Link", "<"+challenge.getAuthorization().getUrl() + ">;rel=\"up\"")
                .body(challenge.mapToDto());
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String raProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException {
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
    public ResponseEntity<Order> getOrder(String raProfileName, String orderId) throws NotFoundException {
        AcmeOrder order = extendedAcmeHelperService.getAcmeOrderEntity(orderId);
        return ResponseEntity
                .ok()
                .location(URI.create(order.getUrl()))
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String raProfileName, String certificateId) throws NotFoundException, CertificateException {
        elevatePermission();
        ByteArrayResource byteArrayResource = extendedAcmeHelperService.getCertificateResource(certificateId);
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
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
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPERADMINISTRATOR"));
        Authentication reAuth = new UsernamePasswordAuthenticationToken(SecurityContextHolder.getContext().getAuthentication().getPrincipal(),"",authorities);
        SecurityContextHolder.getContext().setAuthentication(reAuth);
        Authentication role = SecurityContextHolder.getContext().getAuthentication();
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
