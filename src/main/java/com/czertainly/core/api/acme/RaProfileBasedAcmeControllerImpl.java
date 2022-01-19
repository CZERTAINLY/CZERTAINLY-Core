package com.czertainly.core.api.acme;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.acme.AcmeController;
import com.czertainly.api.interfaces.core.acme.RaProfileBasedAcmeController;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.core.service.acme.AcmeService;
import com.czertainly.core.util.AcmeRandomGeneratorAndValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;

/**
 * This class contains the methods for ACME Implementation. The class implements
 * AcmeController defined in the interface project
 */
@RestController
public class RaProfileBasedAcmeControllerImpl implements RaProfileBasedAcmeController {

    @ModelAttribute
    public void setResponseHeader(HttpServletResponse response) {
        String linkUrl = String.join("/", Arrays.copyOfRange(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().split("/"), 0, 8));
        response.addHeader("Link", "<"+linkUrl + ">;rel=\"index\"");
    }

    private static final String NONCE_HEADER_NAME = "Replay-Nonce";

    @Autowired
    private AcmeService acmeService;

    @Override
    public ResponseEntity<Directory> getDirectory(@PathVariable String raProfileName) throws NotFoundException {
        Directory directory = acmeService.getDirectory(raProfileName);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce()).body(directory);
    }

    @Override
    public ResponseEntity<?> getNonce(String raProfileName) {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce()).build();

    }

    @Override
    public ResponseEntity<?> headNonce(String raProfileName) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce()).build();
    }

    @Override
    public ResponseEntity<?> newAccount(String raProfileName, String jwsBody) throws AcmeProblemDocumentException, NotFoundException {
        return acmeService.newAccount(raProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String raProfileName, String accountId, String jwsBody) throws AcmeProblemDocumentException, NotFoundException {
        return acmeService.updateAccount(raProfileName, accountId, jwsBody);
    }

    @Override
    public ResponseEntity<?> keyRollover(String raProfileName, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.keyRollover(raProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<Order> newOrder(String raProfileName, String jwsBody) throws AcmeProblemDocumentException, NotFoundException {
        return acmeService.newOrder(raProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String raProfileName, String accountId) throws NotFoundException {
        return acmeService.listOrders(raProfileName, accountId);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorizations(String raProfileName, String authorizationId, String jwsBody) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getAuthorization(raProfileName, authorizationId, jwsBody);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String raProfileName, String challengeId) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException {
        return acmeService.validateChallenge(raProfileName, challengeId);
    }

    @Override
    public ResponseEntity<Order> getOrder(String raProfileName, String orderId) throws NotFoundException {
        return acmeService.getOrder(raProfileName, orderId);
    }

    @Override
    public ResponseEntity<Order> finalize(String raProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException {
        return acmeService.finalizeOrder(raProfileName, orderId, jwsBody);
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String raProfileName, String certificateId) throws NotFoundException, CertificateException {
        return acmeService.downloadCertificate(raProfileName, certificateId);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String raProfileName, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, CertificateException {
        return acmeService.revokeCertificate(raProfileName, jwsBody);
    }
}
