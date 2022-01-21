package com.czertainly.core.service.acme;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

/**
 * Acme service class for method implementations. This service will be used by
 * ACME Service Impl classes to converge data and perform operations
 */
public interface RaProfileBasedAcmeService {
    ResponseEntity<Directory> getDirectory(String raProfileName) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<?> getNonce();

    ResponseEntity<Account> newAccount(String raProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException;

    ResponseEntity<Account> updateAccount(String raProfileName, String accountId, String requestJson) throws AcmeProblemDocumentException, NotFoundException;

    ResponseEntity<?> keyRollover(String raProfileName, String jwsBody) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Order> newOrder(String raProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException;

    ResponseEntity<List<Order>> listOrders(String raProfileName, String accountId) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Authorization> getAuthorization(String raProfileName, String authorizationId, String jwsBody) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Challenge> validateChallenge(String raProfileName, String challengeId) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, AcmeProblemDocumentException;

    ResponseEntity<Order> finalizeOrder(String raProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException;

    ResponseEntity<Order> getOrder(String raProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException;

    ResponseEntity<Resource> downloadCertificate(String raProfileName, String certificateId) throws NotFoundException, CertificateException;

    ResponseEntity<?> revokeCertificate(String raProfileName, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, CertificateException;
}
