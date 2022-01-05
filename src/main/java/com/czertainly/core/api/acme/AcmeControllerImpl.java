package com.czertainly.core.api.acme;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.acme.AcmeController;
import com.czertainly.api.model.core.acme.Authorization;
import com.czertainly.api.model.core.acme.Challenge;
import com.czertainly.api.model.core.acme.Directory;
import com.czertainly.api.model.core.acme.Order;
import com.czertainly.core.service.acme.AcmeService;
import com.czertainly.core.util.AcmeRandomGeneratorAndValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class contains the methods for ACME Implementation. The calss implements
 * AcmeController defined in the interface project
 */
@RestController
public class AcmeControllerImpl implements AcmeController {

    private static final String NONCE_HEADER_NAME = "Replay-Nonce";

    @Autowired
    private AcmeService acmeService;

    @Override
    public ResponseEntity<Directory> getDirectory(@PathVariable String acmeProfileName) {
        Directory directory = acmeService.getDirectory(acmeProfileName);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce()).body(directory);
    }

    @Override
    public ResponseEntity<?> getNonce(String acmeProfileName) {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce()).build();

    }

    @Override
    public ResponseEntity<?> headNonce(String acmeProfileName) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(NONCE_HEADER_NAME, AcmeRandomGeneratorAndValidator.generateNonce()).build();
    }

    @Override
    public ResponseEntity<?> newAccount(String acmeProfileName, String jwsBody) throws AcmeProblemDocumentException, NotFoundException {
        return acmeService.newAccount(acmeProfileName, jwsBody);
    }

    @Override
    public void updateAccount(String acmeProfileName, String jwsBody) {

    }

    @Override
    public void keyRollover(String acmeProfileName) {

    }

    @Override
    public ResponseEntity<Order> newOrder(String acmeProfileName, String jwsBody) throws AcmeProblemDocumentException {
        return acmeService.newOrder(acmeProfileName, jwsBody);
    }

    @Override
    public void listOrders(String acmeProfileName, String accountId) {

    }

    @Override
    public ResponseEntity<Authorization> getAuthorizations(String acmeProfileName, String authorizationId) throws NotFoundException {
        return acmeService.getAuthorization(acmeProfileName, authorizationId);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId) throws NotFoundException {
        return acmeService.validateChallenge(acmeProfileName, challengeId);
    }

    @Override
    public ResponseEntity<Order> finalize(String acmeProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, NotFoundException, JsonProcessingException {
        return acmeService.finalizeOrder(acmeProfileName, orderId, jwsBody);
    }

    @Override
    public void downloadCertificate(String acmeProfileName, String certificateId) {

    }
}
