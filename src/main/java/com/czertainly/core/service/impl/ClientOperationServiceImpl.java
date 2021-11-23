package com.czertainly.core.service.impl;

import com.czertainly.api.CertificateApiClient;
import com.czertainly.api.EndEntityApiClient;
import com.czertainly.api.core.modal.*;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.NameAndIdDto;
import com.czertainly.api.model.ca.*;
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
@Secured({"ROLE_CLIENT"})
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
    public ClientCertificateSignResponseDto issueCertificate(String raProfileName, ClientCertificateSignRequestDto request) throws NotFoundException, AlreadyExistException, CertificateException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        CertificateSignRequestDto caRequest = new CertificateSignRequestDto();
        caRequest.setUsername(request.getUsername());
        caRequest.setPassword(request.getPassword());
        caRequest.setPkcs10(request.getPkcs10());

        CertificateSignResponseDto caResponse = certificateApiClient.issueCertificate(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                getEndEntityProfileName(raProfile),
                caRequest);

        Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
        logger.info("Certificate Created. Adding the certificate to Inventory");
        UuidDto dto = new UuidDto();
        dto.setUuid(raProfile.getUuid());
        logger.debug("Id of the certificate is {}", certificate.getId());
        logger.debug("Id of the RA Profile is {}", raProfile.getId());
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e){
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }

        ClientCertificateSignResponseDto response = new ClientCertificateSignResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.REVOKE)
    public void revokeCertificate(String raProfileName, ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        CertRevocationDto caRequest = new CertRevocationDto();
        caRequest.setCertificateSN(request.getCertificateSN());
        caRequest.setIssuerDN(request.getIssuerDN());
        caRequest.setReason(request.getReason());

        certificateApiClient.revokeCertificate(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                getEndEntityProfileName(raProfile),
                caRequest);

        certificateService.revokeCertificate(request.getCertificateSN());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.REQUEST)
    public List<ClientEndEntityDto> listEntities(String raProfileName) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        List<EndEntityDto> endEntities = endEntityApiClient.listEntities(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                getEndEntityProfileName(raProfile));

        return endEntities == null ? null : endEntities.stream()
                .map(e -> mapEndEntity(e))
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.CREATE)
    public void addEndEntity(String raProfileName, ClientAddEndEntityRequestDto request) throws NotFoundException, ConnectorException {
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
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                getEndEntityProfileName(raProfile),
                caRequest);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.REQUEST)
    public ClientEndEntityDto getEndEntity(String raProfileName, String username) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        EndEntityDto endEntity = endEntityApiClient.getEndEntity(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                getEndEntityProfileName(raProfile),
                username);

        return endEntity == null ? null : mapEndEntity(endEntity);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.CHANGE)
    public void editEndEntity(String raProfileName, String username, ClientEditEndEntityRequestDto request) throws NotFoundException, ConnectorException {
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
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                getEndEntityProfileName(raProfile),
                username,
                caRequest);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY, operation = OperationType.DELETE)
    public void revokeAndDeleteEndEntity(String raProfileName, String username) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        endEntityApiClient.revokeAndDeleteEndEntity(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
                getEndEntityProfileName(raProfile),
                username);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ACCESS, operation = OperationType.RESET)
    public void resetPassword(String raProfileName, String username) throws NotFoundException, ConnectorException {
        ValidatorUtil.validateAuthToRaProfile(raProfileName);
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        endEntityApiClient.resetPassword(
                raProfile.getCaInstanceReference().getConnector().mapToDto(),
                raProfile.getCaInstanceReference().getCaInstanceId(),
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
            NameAndIdDto endEntityProfile = AttributeDefinitionUtils.getNameAndIdValue("endEntityProfile", attributes);
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
