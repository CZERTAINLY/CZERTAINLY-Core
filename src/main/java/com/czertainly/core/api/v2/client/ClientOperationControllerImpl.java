package com.czertainly.core.api.v2.client;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.client.v2.ClientOperationController;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
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
            String authorityUuid,
            String raProfileUuid) throws ConnectorException {
        return clientOperationService.listIssueCertificateAttributes(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public void validateIssueCertificateAttributes(
            String authorityUuid,
            String raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException {
        clientOperationService.validateIssueCertificateAttributes(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid), attributes);
    }

    @Override
    public ClientCertificateDataResponseDto issueCertificate(
            String authorityUuid,
            String raProfileUuid,
            ClientCertificateSignRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException {
        return clientOperationService.issueCertificate(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid), request);
    }

    @Override
    public ClientCertificateDataResponseDto renewCertificate(
            String authorityUuid,
            String raProfileUuid,
            String certificateUuid,
            ClientCertificateRenewRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException {
        return clientOperationService.renewCertificate(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid), certificateUuid, request);
    }

    @Override
    public List<AttributeDefinition> listRevokeCertificateAttributes(
            String authorityUuid,
            String raProfileUuid) throws ConnectorException {
        return clientOperationService.listRevokeCertificateAttributes(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid));
    }

    @Override
    public void validateRevokeCertificateAttributes(
            String authorityUuid,
            String raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException {
        clientOperationService.validateRevokeCertificateAttributes(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid), attributes);
    }

    @Override
    public void revokeCertificate(
            String authorityUuid,
            String raProfileUuid,
            String certificateUuid,
            ClientCertificateRevocationDto request) throws ConnectorException {
        clientOperationService.revokeCertificate(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid), certificateUuid, request);
    }
}