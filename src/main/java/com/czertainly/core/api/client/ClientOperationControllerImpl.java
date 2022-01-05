package com.czertainly.core.api.client;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.client.ClientOperationController;
import com.czertainly.api.model.client.authority.*;
import com.czertainly.core.service.ClientOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateException;
import java.util.List;

@RestController
public class ClientOperationControllerImpl implements ClientOperationController {

    @Autowired
    private ClientOperationService clientOperationService;

    @Override
    public ClientCertificateSignResponseDto issueCertificate(
            @PathVariable String raProfileName,
            @RequestBody ClientCertificateSignRequestDto request)
            throws NotFoundException, CertificateException, AlreadyExistException, ConnectorException {
        return clientOperationService.issueCertificate(raProfileName, request);
    }

    @Override
    public void revokeCertificate(@PathVariable String raProfileName, @RequestBody ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        clientOperationService.revokeCertificate(raProfileName, request);
    }

    @Override
    public List<ClientEndEntityDto> listEntities(
            @PathVariable String raProfileName)
            throws NotFoundException, ConnectorException {
        return clientOperationService.listEntities(raProfileName);
    }

    @Override
    public void addEndEntity(
            @PathVariable String raProfileName,
            @RequestBody ClientAddEndEntityRequestDto request)
            throws NotFoundException, AlreadyExistException, ConnectorException {
        clientOperationService.addEndEntity(raProfileName, request);
    }

    @Override
    public ClientEndEntityDto getEndEntity(
            @PathVariable String raProfileName,
            @PathVariable String username)
            throws NotFoundException, ConnectorException {
        return clientOperationService.getEndEntity(raProfileName, username);
    }

    @Override
    public void editEndEntity(
            @PathVariable String raProfileName,
            @PathVariable String username,
            @RequestBody ClientEditEndEntityRequestDto request)
            throws NotFoundException, ConnectorException {
        clientOperationService.editEndEntity(raProfileName, username, request);
    }

    @Override
    public void revokeAndDeleteEndEntity(@PathVariable String raProfileName, @PathVariable String username) throws NotFoundException, ConnectorException {
        clientOperationService.revokeAndDeleteEndEntity(raProfileName, username);
    }

    @Override
    public void resetPassword(@PathVariable String raProfileName, @PathVariable String username) throws NotFoundException, ConnectorException {
        clientOperationService.resetPassword(raProfileName, username);
    }
}
