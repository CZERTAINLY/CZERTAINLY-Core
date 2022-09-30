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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map;

@RestController
public class AcmeControllerImpl implements AcmeController {

    @ModelAttribute
    public void setResponseHeader(HttpServletRequest request, HttpServletResponse response) {
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String linkUrl;
        Map pathVariables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if(pathVariables.containsKey("acmeProfileName")){
            linkUrl = baseUri + "/acme/"+ pathVariables.get("acmeProfileName") + "/directory";
        }else{
            linkUrl = baseUri + "/acme/raProfile/"+ pathVariables.get("acmeProfileName") + "/directory";
        }
        response.addHeader("Link", "<"+linkUrl + ">;rel=\"index\"");
    }

    private static final String NONCE_HEADER_NAME = "Replay-Nonce";

    @Autowired
    private AcmeService acmeService;

    @Override
    public ResponseEntity<Directory> getDirectory(@PathVariable String acmeProfileName) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getDirectory(acmeProfileName);

    }

    @Override
    public ResponseEntity<?> getNonce(String acmeProfileName) {
        return acmeService.getNonce(false);

    }

    @Override
    public ResponseEntity<?> headNonce(String acmeProfileName) {
        return acmeService.getNonce(true);
    }

    @Override
    public ResponseEntity<Account> newAccount(String acmeProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newAccount(acmeProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String acmeProfileName, String accountId, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.updateAccount(acmeProfileName, accountId, jwsBody);
    }

    @Override
    public ResponseEntity<?> keyRollover(String acmeProfileName, String jwsBody) throws
            NotFoundException, AcmeProblemDocumentException {
        return acmeService.keyRollover(acmeProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<Order> newOrder(String acmeProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newOrder(acmeProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String acmeProfileName, String accountId) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.listOrders(acmeProfileName, accountId);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorizations(String acmeProfileName, String authorizationId, String jwsBody)
            throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getAuthorization(acmeProfileName, authorizationId, jwsBody);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String acmeProfileName, String challengeId)
            throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, AcmeProblemDocumentException {
        return acmeService.validateChallenge(acmeProfileName, challengeId);
    }

    @Override
    public ResponseEntity<Order> getOrder(String acmeProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getOrder(acmeProfileName, orderId);
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String acmeProfileName, String orderId, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException {
        return acmeService.finalizeOrder(acmeProfileName, orderId, jwsBody);
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String acmeProfileName, String certificateId) throws
            NotFoundException, CertificateException {
        return acmeService.downloadCertificate(acmeProfileName, certificateId);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String acmeProfileName, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, CertificateException {
        return acmeService.revokeCertificate(acmeProfileName, jwsBody);
    }
}
