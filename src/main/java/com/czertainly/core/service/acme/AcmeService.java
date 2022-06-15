package com.czertainly.core.service.acme;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.Account;
import com.czertainly.api.model.core.acme.Authorization;
import com.czertainly.api.model.core.acme.Challenge;
import com.czertainly.api.model.core.acme.Directory;
import com.czertainly.api.model.core.acme.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public interface AcmeService {
    ResponseEntity<Directory> getDirectory(String acmeProfileName) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<?> getNonce(Boolean isHead);

    ResponseEntity<Account> newAccount(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException;

    ResponseEntity<Account> updateAccount(String acmeProfileName, String accountId, String requestJson) throws AcmeProblemDocumentException, NotFoundException;

    ResponseEntity<?> keyRollover(String acmeProfileName, String jwsBody) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Order> newOrder(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException;

    ResponseEntity<List<Order>> listOrders(String acmeProfileName, String accountId) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Authorization> getAuthorization(String acmeProfileName, String authorizationId, String jwsBody) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, AcmeProblemDocumentException;

    ResponseEntity<Order> finalizeOrder(String acmeProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException;

    ResponseEntity<Order> getOrder(String acmeProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws NotFoundException, CertificateException;

    ResponseEntity<?> revokeCertificate(String acmeProfileName, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, CertificateException;
}
