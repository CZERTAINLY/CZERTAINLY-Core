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
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.service.v2.ExtendedAttributeService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.ValidatorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;

@Service("clientOperationServiceImplV2")
@Transactional
@Secured({"ROLE_CLIENT", "ROLE_ACME"})
public class ClientOperationServiceImpl implements ClientOperationService {
    private static final Logger logger = LoggerFactory.getLogger(ClientOperationServiceImpl.class);

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateApiClient certificateApiClient;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateEventHistoryService certificateEventHistoryService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertValidationService certValidationService;
    @Autowired
    private ExtendedAttributeService extendedAttributeService;


    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    public List<AttributeDefinition> listIssueCertificateAttributes(String raProfileUuid) throws NotFoundException, ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        return extendedAttributeService.listIssueCertificateAttributes(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public boolean validateIssueCertificateAttributes(String raProfileUuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        return extendedAttributeService.validateIssueCertificateAttributes(raProfile, attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.ISSUE)
    public ClientCertificateDataResponseDto issueCertificate(String raProfileUuid, ClientCertificateSignRequestDto request, Boolean ignoreAuthToRa) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        if(!ignoreAuthToRa) {
            ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        }
        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());

        CertificateSignRequestDto caRequest = new CertificateSignRequestDto();
        caRequest.setPkcs10(request.getPkcs10());
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));

        CertificateDataResponseDto caResponse = certificateApiClient.issueCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                caRequest);

        //Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
        Certificate certificate = certificateService.checkCreateCertificateWithMeta(caResponse.getCertificateData(), caResponse.getMeta());
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("CSR", CertificateUtil.formatCsr(caRequest.getPkcs10()));
        certificateEventHistoryService.addEventHistory(CertificateEvent.ISSUE, CertificateEventStatus.SUCCESS, "Issued using RA Profile " + raProfile.getName(), MetaDefinitions.serialize(additionalInformation), certificate);

        logger.info("Certificate created {}", certificate);
        CertificateUpdateRAProfileDto dto = new CertificateUpdateRAProfileDto();
        dto.setRaProfileUuid(raProfile.getUuid());
        logger.debug("Certificate : {}, RA Profile: {}", certificate, raProfile);
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e){
            logger.warn("Unable to validate the uploaded Certificate, {}", e.getMessage());
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
        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());
        logger.debug("Renewing Certificate: ", oldCertificate.toString());
        CertificateRenewRequestDto caRequest = new CertificateRenewRequestDto();
        caRequest.setPkcs10(request.getPkcs10());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        caRequest.setCertificate(oldCertificate.getCertificateContent().getContent());
        caRequest.setMeta(oldCertificate.getMeta());

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("CSR", CertificateUtil.formatCsr(caRequest.getPkcs10()));
        Certificate certificate = null;
        CertificateDataResponseDto caResponse = null;
        try {
            caResponse = certificateApiClient.renewCertificate(
                    raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                    raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                    caRequest);
            //certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
            certificate = certificateService.checkCreateCertificateWithMeta(caResponse.getCertificateData(), caResponse.getMeta());
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.SUCCESS, "Renewed using RA Profile " + raProfile.getName(), MetaDefinitions.serialize(additionalInformation), certificate);
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.SUCCESS, "Renewed using RA Profile " + raProfile.getName(), "New Certificate is issued with Serial Number: " + certificate.getSerialNumber(), oldCertificate);
        } catch (Exception e){
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.FAILED, e.getMessage(), MetaDefinitions.serialize(additionalInformation), oldCertificate);
            logger.error("Failed to renew Certificate", e.getMessage());
            return null;
        }

        logger.info("Certificate Renewed: {}", certificate);
        CertificateUpdateRAProfileDto dto = new CertificateUpdateRAProfileDto();
        dto.setRaProfileUuid(raProfile.getUuid());
        logger.debug("Certificate : {}, RA Profile: {}", certificate, raProfile);
        certificateService.updateRaProfile(certificate.getUuid(), dto);
        certificateService.updateIssuer();
        try {
            certValidationService.validate(certificate);
        } catch (Exception e){
            logger.warn("Unable to validate the uploaded Certificate, {}", e.getMessage());
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
        return extendedAttributeService.listRevokeCertificateAttributes(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    public boolean validateRevokeCertificateAttributes(String raProfileUuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        return extendedAttributeService.validateRevokeCertificateAttributes(raProfile, attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.REVOKE)
    public void revokeCertificate(String raProfileUuid, String certificateUuid, ClientCertificateRevocationDto request, Boolean ignoreAuthToRa) throws NotFoundException, ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        if(!ignoreAuthToRa) {
            ValidatorUtil.validateAuthToRaProfile(raProfile.getName());
        }
        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);
        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());
        logger.debug("Revoking Certificate: ", certificate.toString());

        CertRevocationDto caRequest = new CertRevocationDto();
        caRequest.setReason(request.getReason());
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        caRequest.setCertificate(certificate.getCertificateContent().getContent());
        try {
            certificateApiClient.revokeCertificate(
                    raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                    raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                    caRequest);
            certificateEventHistoryService.addEventHistory(CertificateEvent.REVOKE, CertificateEventStatus.SUCCESS, "Certificate revoked", "", certificate);
        }catch (Exception e){
            certificateEventHistoryService.addEventHistory(CertificateEvent.REVOKE, CertificateEventStatus.FAILED, e.getMessage(), "", certificate);
            logger.error(e.getMessage());
            return;
        }
        try {
            certificate.setStatus(CertificateStatus.REVOKED);
            certificateRepository.save(certificate);
        }catch(Exception e) {
            logger.warn(e.getMessage());
        }
    }
}
