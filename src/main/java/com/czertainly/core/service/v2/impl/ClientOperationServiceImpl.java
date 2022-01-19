package com.czertainly.core.service.v2.impl;

import com.czertainly.api.clients.v2.CertificateApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.CertificateUpdateRAProfileDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.connector.v2.CertRevocationDto;
import com.czertainly.api.model.connector.v2.CertificateDataResponseDto;
import com.czertainly.api.model.connector.v2.CertificateRenewRequestDto;
import com.czertainly.api.model.connector.v2.CertificateSignRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Connector2FunctionGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
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

@Service("clientOperationServiceImplV2")
@Transactional
//@Secured({"ROLE_CLIENT"})
public class ClientOperationServiceImpl implements ClientOperationService {
    private static final Logger logger = LoggerFactory.getLogger(ClientOperationServiceImpl.class);

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateApiClient certificateApiClient;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertValidationService certValidationService;
    @Autowired
    private ConnectorRepository connectorRepository;


    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listIssueCertificateAttributes(String raProfileUuid) throws NotFoundException, ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());

        validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());

        return certificateApiClient.listIssueCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public boolean validateIssueCertificateAttributes(String raProfileUuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());

        validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());

        return certificateApiClient.validateIssueCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                attributes);
    }

    @Override
    public List<AttributeDefinition> mergeAndValidateIssueAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException {
        List<AttributeDefinition> definitions = certificateApiClient.listIssueCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid());

        List<AttributeDefinition> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);

        certificateApiClient.validateIssueCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                attributes);

        return merged;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.ISSUE)
    public ClientCertificateDataResponseDto issueCertificate(String raProfileUuid, ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());

        CertificateSignRequestDto caRequest = new CertificateSignRequestDto();
        caRequest.setPkcs10(request.getPkcs10());
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));

        CertificateDataResponseDto caResponse = certificateApiClient.issueCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                caRequest);

        Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());

        logger.info("Certificate Created. Adding the certificate to Inventory");
        CertificateUpdateRAProfileDto dto = new CertificateUpdateRAProfileDto();
        dto.setRaProfileUuid(raProfile.getUuid());
        logger.debug("Id of the certificate is {}", certificate.getId());
        logger.debug("Id of the RA Profile is {}", raProfile.getId());
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e){
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }

        ClientCertificateDataResponseDto response = new ClientCertificateDataResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        response.setUuid(certificate.getUuid());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.RENEW)
    public ClientCertificateDataResponseDto renewCertificate(String raProfileUuid, String certificateUuid, ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        Certificate oldCertificate = certificateService.getCertificateEntity(certificateUuid);
        validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());

        CertificateRenewRequestDto caRequest = new CertificateRenewRequestDto();
        caRequest.setPkcs10(request.getPkcs10());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        caRequest.setCertificate(oldCertificate.getCertificateContent().getContent());

        CertificateDataResponseDto caResponse = certificateApiClient.renewCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                caRequest);

        Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
        logger.info("Certificate Renewed. Adding the certificate to Inventory");
        CertificateUpdateRAProfileDto dto = new CertificateUpdateRAProfileDto();
        dto.setRaProfileUuid(raProfile.getUuid());
        logger.debug("Id of the certificate is {}", certificate.getId());
        logger.debug("Id of the RA Profile is {}", raProfile.getId());
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e){
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }

        ClientCertificateDataResponseDto response = new ClientCertificateDataResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        response.setUuid(certificate.getUuid());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listRevokeCertificateAttributes(String raProfileUuid) throws NotFoundException, ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());

        return certificateApiClient.listRevokeCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid());
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public boolean validateRevokeCertificateAttributes(String raProfileUuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());

        return certificateApiClient.validateRevokeCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                attributes);
    }

    @Override
    public List<AttributeDefinition> mergeAndValidateRevokeAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException {
        List<AttributeDefinition> definitions = certificateApiClient.listRevokeCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid());

        List<AttributeDefinition> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);

        certificateApiClient.validateRevokeCertificateAttributes(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                attributes);

        return merged;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.REVOKE)
    public void revokeCertificate(String raProfileUuid, String certificateUuid, ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);
        validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());

        logger.debug("Ra Profile {} set for revoking the certificate", raProfile.getName());

        CertRevocationDto caRequest = new CertRevocationDto();
        caRequest.setReason(request.getReason());
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        caRequest.setCertificate(certificate.getCertificateContent().getContent());

        certificateApiClient.revokeCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                caRequest);
        try {
            certificate.setStatus(CertificateStatus.REVOKED);
            certificateRepository.save(certificate);
        }catch(Exception e) {
            logger.warn(e.getMessage());
        }
    }

    private void validateLegacyConnector(Connector connector) throws NotFoundException{
        for(Connector2FunctionGroup fg: connector.getFunctionGroups()){
            if(!connectorRepository.findConnectedByFunctionGroupAndKind(fg.getFunctionGroup(), "LegacyEjbca").isEmpty()){
                throw new NotFoundException("Legacy Authority. V2 Implementation not found on the connector");
            }
        }
    }
}
