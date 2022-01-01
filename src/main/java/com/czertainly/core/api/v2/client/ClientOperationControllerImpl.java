package com.czertainly.core.api.v2.client;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.client.v2.ClientOperationController;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.service.v2.ClientOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateException;
import java.util.List;

@RestController("clientOperationControllerV2")
public class ClientOperationControllerImpl implements ClientOperationController {

    @Autowired
    private ClientOperationService clientOperationService;

    @Override
    public List<AttributeDefinition> listIssueCertificateAttributes(
            @PathVariable String raProfileName) throws NotFoundException, ConnectorException {
        return clientOperationService.listIssueCertificateAttributes(raProfileName);
    }

    @Override
    public void validateIssueCertificateAttributes(
            @PathVariable String raProfileName,
            @RequestBody List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        clientOperationService.validateIssueCertificateAttributes(raProfileName, attributes);
    }

    @Override
    public ClientCertificateDataResponseDto issueCertificate(
            @PathVariable String raProfileName,
            @RequestBody ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        return clientOperationService.issueCertificate(raProfileName, request);
    }

    @Override
    public ClientCertificateDataResponseDto renewCertificate(
            @PathVariable String raProfileName,
            @PathVariable String certificateUuid,
            @RequestBody ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        return clientOperationService.renewCertificate(raProfileName, certificateUuid, request);
    }

    @Override
    public List<AttributeDefinition> listRevokeCertificateAttributes(
            @PathVariable String raProfileName) throws NotFoundException, ConnectorException {
        return clientOperationService.listRevokeCertificateAttributes(raProfileName);
    }

    @Override
    public void validateRevokeCertificateAttributes(
            @PathVariable String raProfileName,
            @RequestBody List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        clientOperationService.validateRevokeCertificateAttributes(raProfileName, attributes);
    }

	@Override
    public void revokeCertificate(
            @PathVariable String raProfileName,
            @PathVariable String certificateUuid,
            @RequestBody ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        clientOperationService.revokeCertificate(raProfileName, certificateUuid, request);
    }
}