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

/**
 * Acme service class for method implementations. This service will be used by
 * ACME Service Impl classes to converge data and perform operations
 */
public interface AcmeService {
    Directory getDirectory(String acmeProfileName);

    ResponseEntity<Account> newAccount(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException, NotFoundException;

    ResponseEntity<Order> newOrder(String acmeProfileName, String requestJson) throws AcmeProblemDocumentException;

    ResponseEntity<Authorization> getAuthorization(String acmeProfileName, String authorizationId) throws NotFoundException;

    ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException;

    ResponseEntity<Order> finalizeOrder(String acmeProfileName, String orderId, String jwsBody) throws AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException;

    ResponseEntity<Order> getOrder(String acmeProfileName, String orderId) throws NotFoundException;

    ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws NotFoundException, CertificateException;
}
