package com.czertainly.core.api.acme;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.acme.AcmeRaProfileController;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

@RestController
public class AcmeRaProfileControllerImpl implements AcmeRaProfileController {

    private AcmeService acmeService;

    @Autowired
    public void setAcmeService(AcmeService acmeService) {
        this.acmeService = acmeService;
    }

    @Override
    public ResponseEntity<Directory> getDirectory(@PathVariable String raProfileName) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getDirectory(raProfileName, getRequestUri(),true);
    }

    @Override
    public ResponseEntity<?> getNonce(String raProfileName) {
        return acmeService.getNonce(raProfileName, false, getRequestUri(), true);

    }

    @Override
    public ResponseEntity<?> headNonce(String raProfileName) {
        return acmeService.getNonce(raProfileName, true, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Account> newAccount(String raProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newAccount(raProfileName, jwsBody, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String raProfileName, String accountId, String jwsBody)
            throws AcmeProblemDocumentException, NotFoundException {
        return acmeService.updateAccount(raProfileName, accountId, jwsBody, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<?> keyRollover(String raProfileName, String jwsBody) throws
            NotFoundException, AcmeProblemDocumentException {
        return acmeService.keyRollover(raProfileName, jwsBody, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Order> newOrder(String raProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newOrder(raProfileName, jwsBody, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String raProfileName, String accountId) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.listOrders(raProfileName, accountId, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorizations(String raProfileName, String authorizationId, String jwsBody)
            throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getAuthorization(raProfileName, authorizationId, jwsBody, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String raProfileName, String challengeId) throws
            NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, AcmeProblemDocumentException {
        return acmeService.validateChallenge(raProfileName, challengeId, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Order> getOrder(String raProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getOrder(raProfileName, orderId, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String raProfileName, String orderId, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException {
        return acmeService.finalizeOrder(raProfileName, orderId, jwsBody, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String raProfileName, String certificateId) throws
            NotFoundException, CertificateException {
        return acmeService.downloadCertificate(raProfileName, certificateId, getRequestUri(), true);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String raProfileName, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, CertificateException {
        return acmeService.revokeCertificate(raProfileName, jwsBody, getRequestUri(), true);
    }

    private URI getRequestUri() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri();
    }
}
