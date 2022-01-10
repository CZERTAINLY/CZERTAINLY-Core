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
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertValidationService certValidationService;

    @Override
    public Directory getDirectory(String acmeProfileName) {
        return frameDirectory(acmeProfileName);
    }

    @Override
    public ResponseEntity<Account> newAccount(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        return processNewAccount(acmeProfileName, requestJson);
    }

    @Override
    public ResponseEntity<Order> newOrder(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        return processNewOrder(acmeProfileName, requestJson);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorization(String acmeProfileName, String authorizationId) throws NotFoundException {
        AcmeAuthorization authorization = acmeAuthorizationRepository.findByAuthorizationId(authorizationId).orElseThrow(() ->new NotFoundException(Authorization.class, authorizationId));
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(authorization.mapToDto());
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
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("SUPERADMINISTRATOR"));
        authorities.add(new SimpleGrantedAuthority("CLIENT"));
        authorities.add(new SimpleGrantedAuthority("ROLE_CLIENT"));
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPERADMINISTRATOR"));

        Authentication reAuth = new UsernamePasswordAuthenticationToken(SecurityContextHolder.getContext().getAuthentication().getPrincipal(),"",authorities);

        SecurityContextHolder.getContext().setAuthentication(reAuth);

        Authentication role = SecurityContextHolder.getContext().getAuthentication();


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
        AcmeOrder order = getAcmeOrderEntity(orderId);
        return ResponseEntity
                .ok()
                .location(URI.create(order.getUrl()))
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(order.mapToDto());
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws NotFoundException, CertificateException {

        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("SUPERADMINISTRATOR"));
        authorities.add(new SimpleGrantedAuthority("CLIENT"));
        authorities.add(new SimpleGrantedAuthority("ROLE_CLIENT"));
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPERADMINISTRATOR"));

        Authentication reAuth = new UsernamePasswordAuthenticationToken(SecurityContextHolder.getContext().getAuthentication().getPrincipal(),"",authorities);

        SecurityContextHolder.getContext().setAuthentication(reAuth);

        Authentication role = SecurityContextHolder.getContext().getAuthentication();


        AcmeOrder order = acmeOrderRepository.findByCertificateId(certificateId).orElseThrow(() -> new NotFoundException(Order.class, certificateId));
        Certificate certificate = order.getCertificateReference();
        List<Certificate> chain = certValidationService.getCertificateChain(certificate);
        String chainString = frameCertChainString(chain);
        ByteArrayResource byteArrayResource = new ByteArrayResource(chainString.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity
                .ok()
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .contentType(MediaType.valueOf("application/pem-certificate-chain"))
                .body(byteArrayResource);
    }

    private Directory frameDirectory(String acmeProfileName) {
        Directory directory = new Directory();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        directory.setNewNonce(String.format("%s/acme/%s/new-nonce", baseUri, acmeProfileName));
        directory.setNewAccount(String.format("%s/acme/%s/new-account", baseUri, acmeProfileName));
        directory.setNewOrder(String.format("%s/acme/%s/new-order", baseUri, acmeProfileName));
        directory.setNewAuthz(String.format("%s/acme/%s/new-authz", baseUri, acmeProfileName));
        directory.setRevokeCert(String.format("%s/acme/%s/revoke-cert", baseUri, acmeProfileName));
        directory.setKeyChange(String.format("%s/acme/%s/ey-change", baseUri, acmeProfileName));
        directory.setMeta(frameDirectoryMeta());
        return directory;
    }

    private DirectoryMeta frameDirectoryMeta() {
        DirectoryMeta meta = new DirectoryMeta();
        meta.setCaaIdentities(Arrays.asList("example.com"));
        meta.setTermsOfService("https://example.com/tos");
        meta.setExternalAccountRequired(false);
        meta.setWebsite("https://czertainly.com");
        return meta;
    }

    private ResponseEntity<Account> processNewAccount(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException {
        newAccountValidator(acmeProfileName, requestJson);
        AcmeAccount account = addNewAccount(acmeProfileName, AcmePublicKeyProcessor.publicKeyPemStringFromObject(
                extendedAcmeHelperService.getPublicKey()));
        Account accountDto = account.mapToDto();
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        accountDto.setOrders(String.format("%s/acme/%s/acct/%s/orders", baseUri, acmeProfileName, account.getAccountId()));
        return ResponseEntity
                .created(URI.create(String.format("%s/acme/%s/acct/%s", baseUri, acmeProfileName, account.getAccountId())))
                .header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce())
                .body(accountDto);
    }

    private void newAccountValidator(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        if (requestJson.isEmpty()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument(
                    "urn:ietf:params:acme:error:malformed",
                    "Empty Body",
                    "Request jws is empty for the request"));
        }
        extendedAcmeHelperService.initialize(requestJson);
        extendedAcmeHelperService.newAccountProcess();
        //TODO Add main to validation
        //TODO Terms of Service validation
    }

    private AcmeAccount addNewAccount(String acmeProfileName, String publicKey) throws NotFoundException {
        String accountId = AcmeRandomGeneratorAndValidator.generateRandomId();
        RaProfile raProfile = raProfileRepository.findByUuid("883ef2c3-c9e3-460f-b55b-e00e19fea7a8")
                .orElseThrow(() -> new NotFoundException(RaProfile.class, "883ef2c3-c9e3-460f-b55b-e00e19fea7a8"));
        AcmeAccount account = new AcmeAccount();
        //TODO set RA Profile and Set ACME Profile
        account.setStatus(AccountStatus.VALID);
        account.setTermsOfServiceAgreed(true);
        account.setRaProfile(raProfile);
        account.setPublicKey(publicKey);
        account.setDefaultRaProfile(true);
        account.setAccountId(accountId);
        account.setRaProfile(raProfile);
        acmeAccountRepository.save(account);
        return account;
    }

    private ResponseEntity<Order> processNewOrder(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException {
        extendedAcmeHelperService.initialize(requestJson);
        String[] acmeAccountKeyIdSegment = extendedAcmeHelperService.getJwsObject().getHeader().getKeyID().split("/");
        String acmeAccountId = acmeAccountKeyIdSegment[acmeAccountKeyIdSegment.length - 1];
        AcmeAccount acmeAccount = getAcmeAccountEntity(acmeAccountId);
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String baseUrl = String.format("%s/acme/%s", baseUri, acmeProfileName);
        try {
            extendedAcmeHelperService.setPublicKey(AcmePublicKeyProcessor.publicKeyObjectFromString(acmeAccount.getPublicKey()));
            extendedAcmeHelperService.IsValidSignature();
            System.out.println(extendedAcmeHelperService.getValidSignature());
            AcmeOrder order = extendedAcmeHelperService.generateOrder(baseUrl, acmeAccount);
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

    private AcmeOrder getAcmeOrderEntity(String orderId) throws NotFoundException {
        return acmeOrderRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException(Order.class, orderId));
    }

    private AcmeAccount getAcmeAccountEntity(String accountId) {
        try {
            return acmeAccountRepository.findByAccountId(accountId)
                    .orElseThrow(() -> new NotFoundException(AcmeAccount.class, accountId));
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private X509Certificate getX509(String certificate) throws CertificateException {
        return CertificateUtil.getX509Certificate(certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", ""));
    }

    private String frameCertChainString(List<Certificate> certificates) throws CertificateException {
        List<String> chain = new ArrayList<>();
        for(Certificate certificate: certificates){
            chain.add(X509ObjectToString.toPem(getX509(certificate.getCertificateContent().getContent())));
        }
        return String.join("\r\n", chain);
    }

}
