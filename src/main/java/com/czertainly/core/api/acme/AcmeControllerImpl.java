package com.czertainly.core.api.acme;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.acme.AcmeController;
import com.czertainly.api.model.core.acme.Account;
import com.czertainly.api.model.core.acme.Authorization;
import com.czertainly.api.model.core.acme.Challenge;
import com.czertainly.api.model.core.acme.Directory;
import com.czertainly.api.model.core.acme.Order;
import com.czertainly.core.service.acme.AcmeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

@RestController
public class AcmeControllerImpl implements AcmeController {

    private AcmeService acmeService;

    @Autowired
    public void setAcmeService(AcmeService acmeService) {
        this.acmeService = acmeService;
    }

    @Override
    public ResponseEntity<Directory> getDirectory(String acmeProfileName) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getDirectory(acmeProfileName, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<?> getNonce(String acmeProfileName) {
        return acmeService.getNonce(acmeProfileName, false, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<?> headNonce(String acmeProfileName) {
        return acmeService.getNonce(acmeProfileName, true, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Account> newAccount(String acmeProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newAccount(acmeProfileName, jwsBody, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String acmeProfileName, String accountId, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.updateAccount(acmeProfileName, accountId, jwsBody, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<?> keyRollover(String acmeProfileName, String jwsBody) throws
            NotFoundException, AcmeProblemDocumentException {
        return acmeService.keyRollover(acmeProfileName, jwsBody, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Order> newOrder(String acmeProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newOrder(acmeProfileName, jwsBody, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String acmeProfileName, String accountId) throws
            NotFoundException, AcmeProblemDocumentException {
        return acmeService.listOrders(acmeProfileName, accountId, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorizations(String acmeProfileName, String authorizationId, String jwsBody)
            throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getAuthorization(acmeProfileName, authorizationId, jwsBody, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId)
            throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, AcmeProblemDocumentException {
        return acmeService.validateChallenge(acmeProfileName, challengeId, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Order> getOrder(String acmeProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getOrder(acmeProfileName, orderId, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String acmeProfileName, String orderId, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException {
        return acmeService.finalizeOrder(acmeProfileName, orderId, jwsBody, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws
            NotFoundException, CertificateException {
        return acmeService.downloadCertificate(acmeProfileName, certificateId, getRequestUri(), false);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String acmeProfileName, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, CertificateException {
        return acmeService.revokeCertificate(acmeProfileName, jwsBody, getRequestUri(), false);
    }

    private URI getRequestUri() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri();
    }
}
