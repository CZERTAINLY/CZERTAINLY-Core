package com.czertainly.core.service.impl;

import com.czertainly.api.clients.CertificateApiClient;
import com.czertainly.api.clients.EndEntityApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.authority.ClientAddEndEntityRequestDto;
import com.czertainly.api.model.client.authority.ClientCertificateRevocationDto;
import com.czertainly.api.model.client.authority.ClientCertificateSignRequestDto;
import com.czertainly.api.model.client.authority.ClientCertificateSignResponseDto;
import com.czertainly.api.model.client.authority.ClientEditEndEntityRequestDto;
import com.czertainly.api.model.client.authority.ClientEndEntityDto;
import com.czertainly.api.model.client.certificate.CertificateUpdateRAProfileDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.authority.AddEndEntityRequestDto;
import com.czertainly.api.model.core.authority.CertRevocationDto;
import com.czertainly.api.model.core.authority.CertificateSignRequestDto;
import com.czertainly.api.model.core.authority.CertificateSignResponseDto;
import com.czertainly.api.model.core.authority.EditEndEntityRequestDto;
import com.czertainly.api.model.core.authority.EndEntityDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ClientOperationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.ValidatorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_CLIENT", "ROLE_ACME"})
public class ClientOperationServiceImpl implements ClientOperationService {

    private static final Logger logger = LoggerFactory.getLogger(ClientOperationServiceImpl.class);

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private EndEntityApiClient endEntityApiClient;
    @Autowired
    private CertificateApiClient certificateApiClient;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertValidationService certValidationService;

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.ISSUE)
    public ClientCertificateSignResponseDto issueCertificate(String raProfileName, ClientCertificateSignRequestDto request) throws AlreadyExistException, CertificateException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        CertificateSignRequestDto caRequest = new CertificateSignRequestDto();
        caRequest.setUsername(request.getUsername());
        caRequest.setPassword(request.getPassword());
        caRequest.setPkcs10(request.getPkcs10());

        CertificateSignResponseDto caResponse = certificateApiClient.issueCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                caRequest);

        Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
        logger.info("Certificate Created. Adding the certificate to Inventory");
        CertificateUpdateRAProfileDto dto = new CertificateUpdateRAProfileDto();
        dto.setRaProfileUuid(raProfile.getUuid());
        logger.debug("UUID of the certificate is {}", certificate.getUuid());
        logger.debug("UUID of the RA Profile is {}", raProfile.getUuid());
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e) {
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }

        ClientCertificateSignResponseDto response = new ClientCertificateSignResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.REVOKE)
    public void revokeCertificate(String raProfileName, ClientCertificateRevocationDto request) throws ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        CertRevocationDto caRequest = new CertRevocationDto();
        caRequest.setCertificateSN(request.getCertificateSN());
        caRequest.setIssuerDN(request.getIssuerDN());
        caRequest.setReason(request.getReason());

        certificateApiClient.revokeCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                caRequest);

        certificateService.revokeCertificate(request.getCertificateSN());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.REQUEST)
    public List<ClientEndEntityDto> listEntities(String raProfileName) throws ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        List<EndEntityDto> endEntities = endEntityApiClient.listEntities(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile));

        return endEntities == null ? null : endEntities.stream()
                .map(this::mapEndEntity)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.CREATE)
    public void addEndEntity(String raProfileName, ClientAddEndEntityRequestDto request) throws ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        AddEndEntityRequestDto caRequest = new AddEndEntityRequestDto();
        caRequest.setUsername(request.getUsername());
        caRequest.setPassword(request.getPassword());
        caRequest.setEmail(request.getEmail());
        caRequest.setSubjectDN(request.getSubjectDN());
        caRequest.setSubjectAltName(request.getSubjectAltName());
        caRequest.setExtensionData(request.getExtensionData());
        caRequest.setRaProfile(raProfile.mapToDto());

        endEntityApiClient.createEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                caRequest);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.REQUEST)
    public ClientEndEntityDto getEndEntity(String raProfileName, String username) throws ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        EndEntityDto endEntity = endEntityApiClient.getEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username);

        return endEntity == null ? null : mapEndEntity(endEntity);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.CHANGE)
    public void editEndEntity(String raProfileName, String username, ClientEditEndEntityRequestDto request) throws ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        EditEndEntityRequestDto caRequest = new EditEndEntityRequestDto();
        caRequest.setPassword(request.getPassword());
        caRequest.setEmail(request.getEmail());
        caRequest.setSubjectDN(request.getSubjectDN());
        caRequest.setSubjectAltName(request.getSubjectAltName());
        caRequest.setExtensionData(request.getExtensionData());
        caRequest.setRaProfile(raProfile.mapToDto());

        endEntityApiClient.updateEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username,
                caRequest);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.DELETE)
    public void revokeAndDeleteEndEntity(String raProfileName, String username) throws ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        endEntityApiClient.revokeAndDeleteEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ACCESS, operation = OperationType.RESET)
    public void resetPassword(String raProfileName, String username) throws ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        endEntityApiClient.resetPassword(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username);
    }

    private String getEndEntityProfileName(RaProfile raProfile) {
        List<AttributeDefinition> attributes = AttributeDefinitionUtils.deserialize(raProfile.getAttributes());
        if (attributes == null
                || !AttributeDefinitionUtils.containsAttributeDefinition("endEntityProfile", attributes)) {
            throw new ValidationException(ValidationError.create("EndEntityProfile not found in attributes"));
        }

        try {
            NameAndIdDto endEntityProfile = AttributeDefinitionUtils.getNameAndIdData("endEntityProfile", AttributeDefinitionUtils.getClientAttributes(attributes));
            return endEntityProfile.getName();
        } catch (Exception e) {
            throw new ValidationException(ValidationError.create("EndEntityProfile could not be retrieved from attributes. {}", e.getMessage()));
        }
    }

    private ClientEndEntityDto mapEndEntity(EndEntityDto caDto) {
        ClientEndEntityDto dto = new ClientEndEntityDto();
        dto.setUsername(caDto.getUsername());
        dto.setEmail(caDto.getEmail());
        dto.setSubjectDN(caDto.getSubjectDN());
        dto.setSubjectAltName(caDto.getSubjectAltName());
        dto.setStatus(caDto.getStatus());
        dto.setExtensionData(caDto.getExtensionData());
        return dto;
    }
}
