package com.czertainly.core.api.client;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.client.ClientOperationController;
import com.czertainly.api.model.client.authority.ClientAddEndEntityRequestDto;
import com.czertainly.api.model.client.authority.ClientCertificateRevocationDto;
import com.czertainly.api.model.client.authority.ClientCertificateSignRequestDto;
import com.czertainly.api.model.client.authority.ClientCertificateSignResponseDto;
import com.czertainly.api.model.client.authority.ClientEditEndEntityRequestDto;
import com.czertainly.api.model.client.authority.ClientEndEntityDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
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
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.ISSUE)
    public ClientCertificateSignResponseDto issueCertificate(
            @PathVariable String raProfileName,
            @RequestBody ClientCertificateSignRequestDto request)
            throws NotFoundException, CertificateException, AlreadyExistException, ConnectorException {
        return clientOperationService.issueCertificate(raProfileName, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.REVOKE)
    public void revokeCertificate(@PathVariable String raProfileName, @RequestBody ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        clientOperationService.revokeCertificate(raProfileName, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.LIST_END_ENTITY)
    public List<ClientEndEntityDto> listEntities(
            @PathVariable String raProfileName)
            throws NotFoundException, ConnectorException {
        return clientOperationService.listEntities(raProfileName);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.ADD_END_ENTITY)
    public void addEndEntity(
            @PathVariable String raProfileName,
            @RequestBody ClientAddEndEntityRequestDto request)
            throws NotFoundException, AlreadyExistException, ConnectorException {
        clientOperationService.addEndEntity(raProfileName, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.END_ENTITY_DETAIL)
    public ClientEndEntityDto getEndEntity(
            @PathVariable String raProfileName,
            @PathVariable String username)
            throws NotFoundException, ConnectorException {
        return clientOperationService.getEndEntity(raProfileName, username);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.EDIT_END_ENTITY)
    public void editEndEntity(
            @PathVariable String raProfileName,
            @PathVariable String username,
            @RequestBody ClientEditEndEntityRequestDto request)
            throws NotFoundException, ConnectorException {
        clientOperationService.editEndEntity(raProfileName, username, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.REVOKE_DELETE_END_ENTITY)
    public void revokeAndDeleteEndEntity(@PathVariable String raProfileName, @PathVariable String username) throws NotFoundException, ConnectorException {
        clientOperationService.revokeAndDeleteEndEntity(raProfileName, username);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CERTIFICATE, actionName = ResourceAction.RESET_PASSWORD)
    public void resetPassword(@PathVariable String raProfileName, @PathVariable String username) throws NotFoundException, ConnectorException {
        clientOperationService.resetPassword(raProfileName, username);
    }
}
