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
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateException;
import java.util.List;

@RestController("clientOperationControllerV2")
@Secured({"ROLE_CLIENT"})
public class ClientOperationControllerImpl implements ClientOperationController {

    @Autowired
    private ClientOperationService clientOperationService;

    @Override
    public List<AttributeDefinition> listIssueCertificateAttributes(
            @PathVariable String raProfileUuid) throws NotFoundException, ConnectorException {
        return clientOperationService.listIssueCertificateAttributes(raProfileUuid);
    }

    @Override
    public void validateIssueCertificateAttributes(
            @PathVariable String raProfileUuid,
            @RequestBody List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        clientOperationService.validateIssueCertificateAttributes(raProfileUuid, attributes);
    }

    @Override
    public ClientCertificateDataResponseDto issueCertificate(
            @PathVariable String raProfileUuid,
            @RequestBody ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        return clientOperationService.issueCertificate(raProfileUuid, request, false);
    }

    @Override
    public ClientCertificateDataResponseDto renewCertificate(
            @PathVariable String raProfileUuid,
            @PathVariable String certificateUuid,
            @RequestBody ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        return clientOperationService.renewCertificate(raProfileUuid, certificateUuid, request);
    }

    @Override
    public List<AttributeDefinition> listRevokeCertificateAttributes(
            @PathVariable String raProfileUuid) throws NotFoundException, ConnectorException {
        return clientOperationService.listRevokeCertificateAttributes(raProfileUuid);
    }

    @Override
    public void validateRevokeCertificateAttributes(
            @PathVariable String raProfileUuid,
            @RequestBody List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        clientOperationService.validateRevokeCertificateAttributes(raProfileUuid, attributes);
    }

	@Override
    public void revokeCertificate(
            @PathVariable String raProfileUuid,
            @PathVariable String certificateUuid,
            @RequestBody ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        clientOperationService.revokeCertificate(raProfileUuid, certificateUuid, request, false);
    }
}