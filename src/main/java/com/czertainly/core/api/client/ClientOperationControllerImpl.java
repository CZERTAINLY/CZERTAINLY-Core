package com.czertainly.core.api.client;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.client.ClientOperationController;
import com.czertainly.api.model.client.authority.ClientAddEndEntityRequestDto;
import com.czertainly.api.model.client.authority.LegacyClientCertificateRevocationDto;
import com.czertainly.api.model.client.authority.LegacyClientCertificateSignRequestDto;
import com.czertainly.api.model.client.authority.ClientCertificateSignResponseDto;
import com.czertainly.api.model.client.authority.ClientEditEndEntityRequestDto;
import com.czertainly.api.model.client.authority.ClientEndEntityDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.service.ClientOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

@RestController
public class ClientOperationControllerImpl implements ClientOperationController {

    @Autowired
    private ClientOperationService clientOperationService;

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.CERTIFICATE, operation = Operation.ISSUE)
    public ClientCertificateSignResponseDto issueCertificate(
            @PathVariable String raProfileName,
            @RequestBody LegacyClientCertificateSignRequestDto request)
            throws NotFoundException, CertificateException, AlreadyExistException, ConnectorException, NoSuchAlgorithmException {
        return clientOperationService.issueCertificate(raProfileName, request);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.CERTIFICATE, operation = Operation.REVOKE)
    public void revokeCertificate(@PathVariable String raProfileName, @RequestBody LegacyClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        clientOperationService.revokeCertificate(raProfileName, request);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.RA_PROFILE, operation = Operation.LIST)
    public List<ClientEndEntityDto> listEntities(
            @PathVariable String raProfileName)
            throws NotFoundException, ConnectorException {
        return clientOperationService.listEntities(raProfileName);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.RA_PROFILE, operation = Operation.CREATE)
    public void addEndEntity(
            @PathVariable String raProfileName,
            @RequestBody ClientAddEndEntityRequestDto request)
            throws NotFoundException, AlreadyExistException, ConnectorException {
        clientOperationService.addEndEntity(raProfileName, request);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.RA_PROFILE, operation = Operation.DETAIL)
    public ClientEndEntityDto getEndEntity(
            @LogResource(name = true) @PathVariable String raProfileName,
            @PathVariable String username)
            throws NotFoundException, ConnectorException {
        return clientOperationService.getEndEntity(raProfileName, username);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.RA_PROFILE, operation = Operation.UPDATE)
    public void editEndEntity(
            @LogResource(name = true) @PathVariable String raProfileName,
            @PathVariable String username,
            @RequestBody ClientEditEndEntityRequestDto request)
            throws NotFoundException, ConnectorException {
        clientOperationService.editEndEntity(raProfileName, username, request);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.RA_PROFILE, operation = Operation.DELETE)
    public void revokeAndDeleteEndEntity(@LogResource(name = true) @PathVariable String raProfileName, @PathVariable String username) throws NotFoundException, ConnectorException {
        clientOperationService.revokeAndDeleteEndEntity(raProfileName, username);
    }

    @Override
    @AuditLogged(module = Module.CERTIFICATES, resource = Resource.RA_PROFILE, operation = Operation.UPDATE)
    public void resetPassword(@LogResource(name = true) @PathVariable String raProfileName, @PathVariable String username) throws NotFoundException, ConnectorException {
        clientOperationService.resetPassword(raProfileName, username);
    }
}
