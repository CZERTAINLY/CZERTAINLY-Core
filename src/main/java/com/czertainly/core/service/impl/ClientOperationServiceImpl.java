package com.czertainly.core.service.impl;

import com.czertainly.api.clients.CertificateApiClient;
import com.czertainly.api.clients.EndEntityApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.authority.ClientAddEndEntityRequestDto;
import com.czertainly.api.model.client.authority.LegacyClientCertificateRevocationDto;
import com.czertainly.api.model.client.authority.LegacyClientCertificateSignRequestDto;
import com.czertainly.api.model.client.authority.ClientCertificateSignResponseDto;
import com.czertainly.api.model.client.authority.ClientEditEndEntityRequestDto;
import com.czertainly.api.model.client.authority.ClientEndEntityDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.authority.AddEndEntityRequestDto;
import com.czertainly.api.model.core.authority.CertRevocationDto;
import com.czertainly.api.model.core.authority.CertificateSignRequestDto;
import com.czertainly.api.model.core.authority.CertificateSignResponseDto;
import com.czertainly.api.model.core.authority.EditEndEntityRequestDto;
import com.czertainly.api.model.core.authority.EndEntityDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ClientOperationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

@Service
@Transactional
public class ClientOperationServiceImpl implements ClientOperationService {

    private static final Logger logger = LoggerFactory.getLogger(ClientOperationServiceImpl.class);

    private RaProfileRepository raProfileRepository;
    private EndEntityApiClient endEntityApiClient;
    private CertificateApiClient certificateApiClient;
    private CertificateService certificateService;
    private AttributeEngine attributeEngine;

    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) {
        this.raProfileRepository = raProfileRepository;
    }

    @Autowired
    public void setEndEntityApiClient(EndEntityApiClient endEntityApiClient) {
        this.endEntityApiClient = endEntityApiClient;
    }

    @Autowired
    public void setCertificateApiClient(CertificateApiClient certificateApiClient) {
        this.certificateApiClient = certificateApiClient;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.CREATE)
    public ClientCertificateSignResponseDto issueCertificate(String raProfileName, LegacyClientCertificateSignRequestDto request) throws AlreadyExistException, CertificateException, ConnectorException, NoSuchAlgorithmException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);

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
        logger.debug("UUID of the certificate is {}", certificate.getUuid());
        logger.debug("UUID of the RA Profile is {}", raProfile.getUuid());

        certificate.setRaProfile(raProfile);
        certificateService.validate(certificate);

        ClientCertificateSignResponseDto response = new ClientCertificateSignResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.REVOKE)
    public void revokeCertificate(String raProfileName, LegacyClientCertificateRevocationDto request) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);

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
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.LIST)
    public List<ClientEndEntityDto> listEntities(String raProfileName) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);

        List<EndEntityDto> endEntities = endEntityApiClient.listEntities(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile));

        return endEntities == null ? null : endEntities.stream()
                .map(this::mapEndEntity)
                .toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE)
    public void addEndEntity(String raProfileName, ClientAddEndEntityRequestDto request) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);
        RaProfileDto raProfileDto = raProfile.mapToDto();
        raProfileDto.setAttributes(attributeEngine.getObjectDataAttributesContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid()));

        AddEndEntityRequestDto caRequest = new AddEndEntityRequestDto();
        caRequest.setUsername(request.getUsername());
        caRequest.setPassword(request.getPassword());
        caRequest.setEmail(request.getEmail());
        caRequest.setSubjectDN(request.getSubjectDN());
        caRequest.setSubjectAltName(request.getSubjectAltName());
        caRequest.setExtensionData(request.getExtensionData());
        caRequest.setRaProfile(raProfileDto);

        endEntityApiClient.createEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                caRequest);
    }

    @Override
    public ClientEndEntityDto getEndEntity(String raProfileName, String username) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);

        EndEntityDto endEntity = endEntityApiClient.getEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username);

        return endEntity == null ? null : mapEndEntity(endEntity);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE)
    public void editEndEntity(String raProfileName, String username, ClientEditEndEntityRequestDto request) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);
        RaProfileDto raProfileDto = raProfile.mapToDto();
        raProfileDto.setAttributes(attributeEngine.getObjectDataAttributesContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid()));

        EditEndEntityRequestDto caRequest = new EditEndEntityRequestDto();
        caRequest.setPassword(request.getPassword());
        caRequest.setEmail(request.getEmail());
        caRequest.setSubjectDN(request.getSubjectDN());
        caRequest.setSubjectAltName(request.getSubjectAltName());
        caRequest.setExtensionData(request.getExtensionData());
        caRequest.setStatus(request.getStatus());
        caRequest.setRaProfile(raProfileDto);

        endEntityApiClient.updateEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username,
                caRequest);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE)
    public void revokeAndDeleteEndEntity(String raProfileName, String username) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);

        endEntityApiClient.revokeAndDeleteEndEntity(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE)
    public void resetPassword(String raProfileName, String username) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntityChecked(raProfileName);

        endEntityApiClient.resetPassword(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                getEndEntityProfileName(raProfile),
                username);
    }

    private String getEndEntityProfileName(RaProfile raProfile) {
        var raProfileAttributes = attributeEngine.getRequestObjectDataAttributesContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid());
        if (raProfileAttributes == null || raProfileAttributes.stream().noneMatch(a -> a.getName().equals("endEntityProfile"))) {
            throw new ValidationException(ValidationError.create("EndEntityProfile not found in attributes"));
        }

        try {
            NameAndIdDto endEntityProfile = AttributeDefinitionUtils.getNameAndIdData("endEntityProfile", raProfileAttributes);
            if (endEntityProfile == null) {
                throw new ValidationException(ValidationError.create("EndEntityProfile not found in attributes"));
            }

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

    private RaProfile getRaProfileEntityChecked(String raProfileName) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByNameAndEnabledIsTrue(raProfileName)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileName));

        ((ClientOperationService) AopContext.currentProxy()).checkAccessPermissions(raProfile.getSecuredUuid(), SecuredParentUUID.fromString(raProfile.getAuthorityInstanceReferenceUuid().toString()));

        return raProfile;
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public void checkAccessPermissions(SecuredUUID raProfileUuid, SecuredParentUUID authorityUuid) {

    }
}
