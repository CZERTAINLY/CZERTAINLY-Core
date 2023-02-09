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
import com.czertainly.core.service.acme.AcmeRaProfileService;
import com.czertainly.core.service.acme.impl.ExtendedAcmeHelperService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map;

@RestController
public class AcmeRaProfileControllerImpl implements AcmeRaProfileController {

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
    private AcmeRaProfileService acmeService;
    @Autowired
    private ExtendedAcmeHelperService extendedAcmeHelperService;

    @Override
    public ResponseEntity<Directory> getDirectory(@PathVariable String raProfileName) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getDirectory(raProfileName);
    }

    @Override
    public ResponseEntity<?> getNonce(String raProfileName) {
        return acmeService.getNonce(false);

    }

    @Override
    public ResponseEntity<?> headNonce(String raProfileName) {
        return acmeService.getNonce(true);
    }

    @Override
    public ResponseEntity<Account> newAccount(String raProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newAccount(raProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<Account> updateAccount(String raProfileName, String accountId, String jwsBody)
            throws AcmeProblemDocumentException, NotFoundException {
        return acmeService.updateAccount(raProfileName, accountId, jwsBody);
    }

    @Override
    public ResponseEntity<?> keyRollover(String raProfileName, String jwsBody) throws
            NotFoundException, AcmeProblemDocumentException {
        return acmeService.keyRollover(raProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<Order> newOrder(String raProfileName, String jwsBody) throws
            AcmeProblemDocumentException, NotFoundException {
        return acmeService.newOrder(raProfileName, jwsBody);
    }

    @Override
    public ResponseEntity<List<Order>> listOrders(String raProfileName, String accountId) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.listOrders(raProfileName, accountId);
    }

    @Override
    public ResponseEntity<Authorization> getAuthorizations(String raProfileName, String authorizationId, String jwsBody)
            throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getAuthorization(raProfileName, authorizationId, jwsBody);
    }

    @Override
    public ResponseEntity<Challenge> validateChallenge(String raProfileName, String challengeId) throws
            NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, AcmeProblemDocumentException {
        return acmeService.validateChallenge(raProfileName, challengeId);
    }

    @Override
    public ResponseEntity<Order> getOrder(String raProfileName, String orderId) throws NotFoundException, AcmeProblemDocumentException {
        return acmeService.getOrder(raProfileName, orderId);
    }

    @Override
    public ResponseEntity<Order> finalizeOrder(String raProfileName, String orderId, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, JsonProcessingException, CertificateException, AlreadyExistException {
        return acmeService.finalizeOrder(raProfileName, orderId, jwsBody);
    }

    @Override
    public ResponseEntity<Resource> downloadCertificate(String raProfileName, String certificateId) throws
            NotFoundException, CertificateException {
        return acmeService.downloadCertificate(raProfileName, certificateId);
    }

    @Override
    public ResponseEntity<?> revokeCertificate(String raProfileName, String jwsBody) throws
            AcmeProblemDocumentException, ConnectorException, CertificateException {
        return acmeService.revokeCertificate(raProfileName, jwsBody);
    }
}
