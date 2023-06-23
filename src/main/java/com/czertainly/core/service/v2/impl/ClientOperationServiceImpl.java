package com.czertainly.core.service.v2.impl;

import com.czertainly.api.clients.v2.CertificateApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.CertificateUpdateObjectsDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.api.model.connector.v2.CertRevocationDto;
import com.czertainly.api.model.connector.v2.CertificateDataResponseDto;
import com.czertainly.api.model.connector.v2.CertificateRenewRequestDto;
import com.czertainly.api.model.connector.v2.CertificateSignRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.api.model.core.authority.RevocationReason;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.v2.*;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.attribute.CsrAttributes;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateLocation;
import com.czertainly.core.dao.entity.CertificateRequest;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.*;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.service.v2.ExtendedAttributeService;
import com.czertainly.core.util.*;
import jakarta.transaction.Transactional;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

@Service("clientOperationServiceImplV2")
@Transactional
public class ClientOperationServiceImpl implements ClientOperationService {
    private static final Logger logger = LoggerFactory.getLogger(ClientOperationServiceImpl.class);
    private RaProfileRepository raProfileRepository;
    private CertificateRepository certificateRepository;
    private LocationService locationService;
    private CertificateService certificateService;
    private CertificateEventHistoryService certificateEventHistoryService;
    private ExtendedAttributeService extendedAttributeService;
    private CertValidationService certValidationService;
    private CertificateApiClient certificateApiClient;
    private MetadataService metadataService;
    private AttributeService attributeService;
    private CryptographicOperationService cryptographicOperationService;
    private CryptographicKeyService keyService;

    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) {
        this.raProfileRepository = raProfileRepository;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Lazy
    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setCertificateEventHistoryService(CertificateEventHistoryService certificateEventHistoryService) {
        this.certificateEventHistoryService = certificateEventHistoryService;
    }

    @Autowired
    public void setExtendedAttributeService(ExtendedAttributeService extendedAttributeService) {
        this.extendedAttributeService = extendedAttributeService;
    }

    @Autowired
    public void setCertValidationService(CertValidationService certValidationService) {
        this.certValidationService = certValidationService;
    }

    @Autowired
    public void setCertificateApiClient(CertificateApiClient certificateApiClient) {
        this.certificateApiClient = certificateApiClient;
    }

    @Autowired
    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Autowired
    public void setCryptographicOperationService(CryptographicOperationService cryptographicOperationService) {
        this.cryptographicOperationService = cryptographicOperationService;
    }

    @Autowired
    public void setKeyService(CryptographicKeyService keyService) {
        this.keyService = keyService;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ANY, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public List<BaseAttribute> listIssueCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid) throws ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        return extendedAttributeService.listIssueCertificateAttributes(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ANY, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public boolean validateIssueCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        return extendedAttributeService.validateIssueCertificateAttributes(raProfile, attributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.CREATE)
    public CertificateDetailDto createCsr(ClientCertificateRequestDto request) throws NotFoundException, CertificateException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        Map<String, Object> csrMap = generateCsr(request.getPkcs10(), request.getCsrAttributes(), request.getKeyUuid(), request.getTokenProfileUuid(), request.getSignatureAttributes());
        String pkcs10 = (String) csrMap.get("csr");
        List<DataAttribute> merged = (List<DataAttribute>) csrMap.get("merged");
        Certificate csr = certificateService.createCsr(pkcs10, request.getSignatureAttributes(), merged, request.getKeyUuid());
        certificateEventHistoryService.addEventHistory(CertificateEvent.CREATE_CSR, CertificateEventStatus.SUCCESS, "CSR created with the provided parameters", "", csr);
        return csr.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.ISSUE)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public ClientCertificateDataResponseDto issueNewCertificate(final SecuredParentUUID authorityUuid, final SecuredUUID raProfileUuid, final String certificateUuid) throws ConnectorException, AlreadyExistException, CertificateException, NoSuchAlgorithmException {
        final RaProfile raProfile = getRaProfile(raProfileUuid);

        final Certificate certificateEntity = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        if (certificateEntity.getStatus() != CertificateStatus.NEW) {
            throw new ValidationException(ValidationError.create("Cannot issue New certificate with status: " + certificateEntity.getStatus().getLabel()));
        }
        String pkcs10 = certificateEntity.getCertificateRequest() != null ? certificateEntity.getCertificateRequest().getContent() : null;
        CertificateDataResponseDto caResponse = issueCertificate(pkcs10, null, raProfile); // TODO - issue attributes will be passed after implementation of storing issue attributes for certificate
        Certificate certificate = certificateService.updateCsrToCertificate(certificateEntity.getUuid(), caResponse.getCertificateData(), caResponse.getMeta());

        return getClientCertificateDataResponseDto(raProfile, pkcs10, caResponse, certificate);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.ISSUE)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public ClientCertificateDataResponseDto issueCertificate(final SecuredParentUUID authorityUuid, final SecuredUUID raProfileUuid, final ClientCertificateSignRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, NoSuchAlgorithmException {
        final RaProfile raProfile = getRaProfile(raProfileUuid);

        // the CSR should be properly converted to ensure consistent Base64-encoded format
        final Map<String, Object> csrMap = generateCsr(request.getPkcs10(), request.getCsrAttributes(), request.getKeyUuid(), request.getTokenProfileUuid(), request.getSignatureAttributes());
        String pkcs10 = (String) csrMap.get("csr");
        final List<DataAttribute> merged = (List<DataAttribute>) csrMap.get("merged");
        if (!isProtocolUser()) {
            attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.CERTIFICATE);
        }
        CertificateDataResponseDto caResponse = issueCertificate(pkcs10, request.getAttributes(), raProfile);
        Certificate certificate = certificateService.checkCreateCertificateWithMeta(
                caResponse.getCertificateData(),
                caResponse.getMeta(),
                pkcs10,
                request.getKeyUuid(),
                merged,
                request.getSignatureAttributes(),
                raProfile.getAuthorityInstanceReference().getConnectorUuid(),
                null
        );

        //Create Custom Attributes
        attributeService.createAttributeContent(certificate.getUuid(), request.getCustomAttributes(), Resource.CERTIFICATE);

        return getClientCertificateDataResponseDto(raProfile, pkcs10, caResponse, certificate);
    }

    private RaProfile getRaProfile(SecuredUUID raProfileUuid) throws NotFoundException {
        certificateService.checkIssuePermissions();
        final RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());
        return raProfile;
    }

    private CertificateDataResponseDto issueCertificate(String pkcs10, List<RequestAttributeDto> raProfileAttributes, RaProfile raProfile) throws ConnectorException {
        CertificateSignRequestDto caRequest = new CertificateSignRequestDto();
        caRequest.setPkcs10(pkcs10);
        caRequest.setAttributes(raProfileAttributes);
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        return certificateApiClient.issueCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                caRequest);
    }

    private ClientCertificateDataResponseDto getClientCertificateDataResponseDto(RaProfile raProfile, String pkcs10, CertificateDataResponseDto caResponse, Certificate certificate) throws NotFoundException {
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("CSR", pkcs10);
        certificateEventHistoryService.addEventHistory(CertificateEvent.ISSUE, CertificateEventStatus.SUCCESS, "Issued using RA Profile " + raProfile.getName(), MetaDefinitions.serialize(additionalInformation), certificate);

        logger.info("Certificate created {}", certificate);
        UserProfileDto userProfileDto = AuthHelper.getUserProfile();
        CertificateUpdateObjectsDto dto = new CertificateUpdateObjectsDto();
        dto.setRaProfileUuid(raProfile.getUuid().toString());
        logger.debug("Certificate : {}, RA Profile: {}", certificate, raProfile);
        dto.setOwnerUuid(userProfileDto.getUser().getUuid());
        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), dto);
        certificateService.updateCertificateIssuer(certificate);
        try {
            certValidationService.validate(certificate);
        } catch (Exception e) {
            logger.warn("Unable to validate the uploaded Certificate, {}", e.getMessage());
        }

        final ClientCertificateDataResponseDto response = new ClientCertificateDataResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        response.setUuid(certificate.getUuid().toString());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.RENEW)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public ClientCertificateDataResponseDto renewCertificate(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, String certificateUuid, ClientCertificateRenewRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException {
        certificateService.checkRenewPermissions();
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        Certificate oldCertificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        checkNewStatus(oldCertificate.getStatus());
        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());
        logger.debug("Renewing Certificate: ", oldCertificate.toString());
        CertificateRenewRequestDto caRequest = new CertificateRenewRequestDto();
        // the CSR should be properly converted to ensure consistent Base64-encoded format
        String pkcs10;
        String csr = null;
        UUID keyUuid = oldCertificate.getKeyUuid();
        List<DataAttribute> merged = null;
        List<RequestAttributeDto> signatureAttributes = null;

        // CSR decision making
        // Check if the CSR is uploaded for the renewal
        if (request.getPkcs10() != null) {
            csr = request.getPkcs10();
            validatePublicKeyForCsrAndCertificate(oldCertificate.getCertificateContent().getContent(), csr, true);
            keyUuid = null;
        } else {
            // Check if the request is for using the existing CSR
            csr = getExistingCsr(oldCertificate);
        }

        try {
            pkcs10 = Base64.getEncoder().encodeToString(parseCsrToJcaObject(csr).getEncoded());
        } catch (IOException e) {
            logger.debug("Failed to parse CSR: " + e);
            throw new CertificateException(e);
        }
        caRequest.setPkcs10(pkcs10);
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        caRequest.setCertificate(oldCertificate.getCertificateContent().getContent());
        caRequest.setMeta(metadataService.getMetadataWithSourceForCertificateRenewal(raProfile.getAuthorityInstanceReference().getConnectorUuid(), oldCertificate.getUuid(), Resource.CERTIFICATE, null, null));

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("CSR", pkcs10);
        additionalInformation.put("Parent Certificate UUID", oldCertificate.getUuid());
        additionalInformation.put("Parent Certificate Serial Number", oldCertificate.getSerialNumber());
        Certificate certificate = null;
        CertificateDataResponseDto caResponse = null;
        try {
            caResponse = certificateApiClient.renewCertificate(
                    raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                    raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                    caRequest);
            //certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
            certificate = certificateService.checkCreateCertificateWithMeta(
                    caResponse.getCertificateData(),
                    caResponse.getMeta(),
                    csr,
                    keyUuid,
                    merged,
                    signatureAttributes,
                    raProfile.getAuthorityInstanceReference().getConnectorUuid(),
                    oldCertificate.getUuid()
            );
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.SUCCESS, "Renewed using RA Profile " + raProfile.getName(), MetaDefinitions.serialize(additionalInformation), certificate);
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.SUCCESS, "Renewed using RA Profile " + raProfile.getName(), "New Certificate is issued with Serial Number: " + certificate.getSerialNumber(), oldCertificate);

            /** replace certificate in the locations if needed */
            if (request.isReplaceInLocations()) {
                logger.info("Replacing certificates in locations for certificate: " + certificate.getUuid());
                for (CertificateLocation cl : oldCertificate.getLocations()) {
                    PushToLocationRequestDto pushRequest = new PushToLocationRequestDto();
                    pushRequest.setAttributes(AttributeDefinitionUtils.getClientAttributes(cl.getPushAttributes()));

                    locationService.removeCertificateFromLocation(SecuredParentUUID.fromUUID(cl.getLocation().getEntityInstanceReferenceUuid()), cl.getLocation().getSecuredUuid(), oldCertificate.getUuid().toString());
                    certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_LOCATION, CertificateEventStatus.SUCCESS, "Removed from Location " + cl.getLocation().getName(), "", oldCertificate);

                    locationService.pushCertificateToLocation(SecuredParentUUID.fromUUID(cl.getLocation().getEntityInstanceReferenceUuid()), cl.getLocation().getSecuredUuid(), certificate.getUuid().toString(), pushRequest);
                    certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_LOCATION, CertificateEventStatus.SUCCESS, "Pushed to Location " + cl.getLocation().getName(), "", certificate);
                }
            }

        } catch (Exception e) {
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.FAILED, e.getMessage(), MetaDefinitions.serialize(additionalInformation), oldCertificate);
            logger.error("Failed to renew Certificate", e.getMessage());
            throw new CertificateOperationException("Failed to renew certificate: " + e.getMessage());
        }

        logger.info("Certificate Renewed: {}", certificate);
        CertificateUpdateObjectsDto dto = new CertificateUpdateObjectsDto();
        dto.setRaProfileUuid(raProfile.getUuid().toString());
        logger.debug("Certificate : {}, RA Profile: {}", certificate, raProfile);
        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), dto);
        certificateService.updateCertificateIssuer(certificate);
        try {
            certValidationService.validate(certificate);
        } catch (Exception e) {
            logger.warn("Unable to validate the uploaded Certificate, {}", e.getMessage());
        }

        ClientCertificateDataResponseDto response = new ClientCertificateDataResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        response.setUuid(certificate.getUuid().toString());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.RENEW)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public ClientCertificateDataResponseDto rekeyCertificate(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, String certificateUuid, ClientCertificateRekeyRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException {
        certificateService.checkRenewPermissions();
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        Certificate oldCertificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        checkNewStatus(oldCertificate.getStatus());
        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());
        logger.debug("Rekeying Certificate: ", oldCertificate.toString());
        CertificateRenewRequestDto caRequest = new CertificateRenewRequestDto();
        // the CSR should be properly converted to ensure consistent Base64-encoded format
        String pkcs10;
        String csr = null;
        UUID keyUuid = oldCertificate.getKeyUuid();
        List<RequestAttributeDto> signatureAttributes = null;

        // CSR decision making
        // Check if the CSR is uploaded for the renewal
        if (request.getPkcs10() != null) {
            csr = request.getPkcs10();
            String certificateContent = oldCertificate.getCertificateContent().getContent();
            validatePublicKeyForCsrAndCertificate(certificateContent, csr, false);
            validateSubjectDnForCertificate(certificateContent, csr);
            keyUuid = null;
        } else {
            keyUuid = existingKeyValidation(request.getKeyUuid(), request.getSignatureAttributes(), oldCertificate);
            X509Certificate x509Certificate = CertificateUtil.parseCertificate(oldCertificate.getCertificateContent().getContent());
            X500Principal principal = x509Certificate.getSubjectX500Principal();
            // Gather the signature attributes either provided in the request or get it from the old certificate
            signatureAttributes = request.getSignatureAttributes() != null
                    ? request.getSignatureAttributes()
                    : null;

            if (signatureAttributes == null) {
                signatureAttributes = oldCertificate.getCertificateRequest() != null ? oldCertificate.getCertificateRequest().getSignatureAttributes() : null;
            }

            csr = generateCsr(
                    keyUuid,
                    getTokenProfileUuid(request.getTokenProfileUuid(), oldCertificate),
                    principal,
                    signatureAttributes
            );
        }

        try {
            pkcs10 = Base64.getEncoder().encodeToString(parseCsrToJcaObject(csr).getEncoded());
        } catch (IOException e) {
            logger.debug("Failed to parse CSR: " + e);
            throw new CertificateException(e);
        }
        caRequest.setPkcs10(pkcs10);
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        caRequest.setCertificate(oldCertificate.getCertificateContent().getContent());
        caRequest.setMeta(metadataService.getMetadataWithSourceForCertificateRenewal(raProfile.getAuthorityInstanceReference().getConnectorUuid(), oldCertificate.getUuid(), Resource.CERTIFICATE, null, null));

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("CSR", pkcs10);
        additionalInformation.put("Parent Certificate UUID", oldCertificate.getUuid());
        additionalInformation.put("Parent Certificate Serial Number", oldCertificate.getSerialNumber());
        Certificate certificate = null;
        CertificateDataResponseDto caResponse = null;
        try {
            caResponse = certificateApiClient.renewCertificate(
                    raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                    raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                    caRequest);
            //certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
            certificate = certificateService.checkCreateCertificateWithMeta(
                    caResponse.getCertificateData(),
                    caResponse.getMeta(),
                    csr,
                    keyUuid,
                    null,
                    signatureAttributes,
                    raProfile.getAuthorityInstanceReference().getConnectorUuid(),
                    oldCertificate.getUuid()
            );
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.SUCCESS, "Rekey completed using RA Profile " + raProfile.getName(), MetaDefinitions.serialize(additionalInformation), certificate);
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.SUCCESS, "Rekey completed using RA Profile " + raProfile.getName(), "New Certificate is issued with Serial Number: " + certificate.getSerialNumber(), oldCertificate);

            /** replace certificate in the locations if needed */
            if (request.isReplaceInLocations()) {
                logger.info("Replacing certificates in locations for certificate: " + certificate.getUuid());
                for (CertificateLocation cl : oldCertificate.getLocations()) {
                    PushToLocationRequestDto pushRequest = new PushToLocationRequestDto();
                    pushRequest.setAttributes(AttributeDefinitionUtils.getClientAttributes(cl.getPushAttributes()));

                    locationService.removeCertificateFromLocation(SecuredParentUUID.fromUUID(cl.getLocation().getEntityInstanceReferenceUuid()), cl.getLocation().getSecuredUuid(), oldCertificate.getUuid().toString());
                    certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_LOCATION, CertificateEventStatus.SUCCESS, "Removed from Location " + cl.getLocation().getName(), "", oldCertificate);

                    locationService.pushCertificateToLocation(SecuredParentUUID.fromUUID(cl.getLocation().getEntityInstanceReferenceUuid()), cl.getLocation().getSecuredUuid(), certificate.getUuid().toString(), pushRequest);
                    certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_LOCATION, CertificateEventStatus.SUCCESS, "Pushed to Location " + cl.getLocation().getName(), "", certificate);
                }
            }

        } catch (Exception e) {
            certificateEventHistoryService.addEventHistory(CertificateEvent.RENEW, CertificateEventStatus.FAILED, e.getMessage(), MetaDefinitions.serialize(additionalInformation), oldCertificate);
            logger.error("Failed to rekey Certificate", e.getMessage());
            throw new CertificateOperationException("Failed to rekey certificate: " + e.getMessage());
        }

        logger.info("Certificate Rekey: {}", certificate);
        CertificateUpdateObjectsDto dto = new CertificateUpdateObjectsDto();
        dto.setRaProfileUuid(raProfile.getUuid().toString());
        logger.debug("Certificate : {}, RA Profile: {}", certificate, raProfile);
        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), dto);
        certificateService.updateCertificateIssuer(certificate);
        try {
            certValidationService.validate(certificate);
        } catch (Exception e) {
            logger.warn("Unable to validate the uploaded Certificate, {}", e.getMessage());
        }

        ClientCertificateDataResponseDto response = new ClientCertificateDataResponseDto();
        response.setCertificateData(caResponse.getCertificateData());
        response.setUuid(certificate.getUuid().toString());
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ANY, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public List<BaseAttribute> listRevokeCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid) throws ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        return extendedAttributeService.listRevokeCertificateAttributes(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.ATTRIBUTES, operation = OperationType.VALIDATE)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ANY, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public boolean validateRevokeCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException {
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        return extendedAttributeService.validateRevokeCertificateAttributes(raProfile, attributes);
    }

    @Override
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.REVOKE)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public void revokeCertificate(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, String certificateUuid, ClientCertificateRevocationDto request) throws ConnectorException {
        certificateService.checkRevokePermissions();
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        checkNewStatus(certificate.getStatus());
        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());
        logger.debug("Revoking Certificate: ", certificate.toString());

        CertRevocationDto caRequest = new CertRevocationDto();
        caRequest.setReason(request.getReason());
        if (request.getReason() == null) {
            caRequest.setReason(RevocationReason.UNSPECIFIED);
        }
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        caRequest.setCertificate(certificate.getCertificateContent().getContent());
        try {
            certificateApiClient.revokeCertificate(
                    raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                    raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                    caRequest);
            certificateEventHistoryService.addEventHistory(CertificateEvent.REVOKE, CertificateEventStatus.SUCCESS, "Certificate revoked. Reason: " + request.getReason(), "", certificate);
        } catch (Exception e) {
            certificateEventHistoryService.addEventHistory(CertificateEvent.REVOKE, CertificateEventStatus.FAILED, e.getMessage(), "", certificate);
            logger.error(e.getMessage());
            throw (e);
        }
        try {
            certificate.setStatus(CertificateStatus.REVOKED);
            logger.debug("Certificate revoked. Proceeding to check and destroy key");

            if (certificate.getKey() != null && request.isDestroyKey()) {
                keyService.destroyKey(List.of(certificate.getKeyUuid().toString()));
            }
            certificateRepository.save(certificate);

        } catch (Exception e) {
            logger.warn(e.getMessage());
        }
    }

    /**
     * Function to parse the CSR from string to BC JCA objects
     *
     * @param pkcs10 Base64 encoded csr string
     * @return Jca object
     * @throws IOException
     */
    private JcaPKCS10CertificationRequest parseCsrToJcaObject(String pkcs10) throws IOException {
        JcaPKCS10CertificationRequest csr;
        try {
            csr = CsrUtil.csrStringToJcaObject(pkcs10);
        } catch (IOException e) {
            logger.debug("Failed to parse CSR, will decode and try again...");
            String decodedPkcs10 = new String(Base64.getDecoder().decode(pkcs10));
            csr = CsrUtil.csrStringToJcaObject(decodedPkcs10);
        }
        return csr;
    }

    /**
     * Check and get the CSR from the existing certificate
     *
     * @param certificate Old certificate
     * @return CSR from the old certificate
     */
    private String getExistingCsr(Certificate certificate) {
        if (certificate.getCertificateRequest() == null
                || certificate.getCertificateRequest().getContent() == null) {
            // If the CSR is not found for the existing certificate, then throw error
            throw new ValidationException(
                    ValidationError.create(
                            "CSR does not available for the existing certificate"
                    )
            );
        }
        return certificate.getCertificateRequest().getContent();
    }

    /**
     * Check and get the CSR attributes from the existing certificate
     *
     * @param csrAttributes Existing Certificate CSR Attributes
     * @param certificate   Existing certificate
     * @return List of attributes from the existing certificate
     */
    private List<DataAttribute> getExistingCsrAttributes(List<RequestAttributeDto> csrAttributes, Certificate certificate) {
        // Check if the request is for generating a new CSR.
        // If the CSR attributes are not provided in the request and of the CSR attributes are not available for the
        // existing certificate then throw error
        if (csrAttributes == null || csrAttributes.isEmpty()) {
            final CertificateRequest certificateRequest = certificate.getCertificateRequest();
            if (certificateRequest == null || certificateRequest.getAttributes() == null || certificateRequest.getAttributes().isEmpty()) {
                throw new ValidationException(
                        ValidationError.create(
                                "No CSR Attribute is provided. Existing CSR Attributes for the certificate is also not available"
                        )
                );
            } else {
                // If the CSR of the existing certificate is found, then use it
                return certificateRequest.getAttributes();
            }
        } else {
            // If new set of CSR attributes are found for the request, use it to create the new CSR
            AttributeDefinitionUtils.validateAttributes(CsrAttributes.csrAttributes(), csrAttributes);
            return AttributeDefinitionUtils.mergeAttributes(CsrAttributes.csrAttributes(), csrAttributes);
        }
    }

    private UUID getTokenProfileUuid(UUID tokenProfileUuid, Certificate certificate) {
        if (certificate.getKeyUuid() == null && tokenProfileUuid == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Token Profile cannot be empty for creating new CSR"
                    )
            );
        }
        return tokenProfileUuid != null ? tokenProfileUuid : certificate.getKey().getTokenProfile().getUuid();
    }

    /**
     * Validate existing key from the old certificate
     *
     * @param keyUuid             Key UUID
     * @param signatureAttributes Signature Attributes
     * @param certificate         Existing certificate to be renewed
     * @return UUID of the key from the old certificate
     */
    private UUID existingKeyValidation(UUID keyUuid, List<RequestAttributeDto> signatureAttributes, Certificate certificate) {
        // If the signature attributes are not provided in the request and not available in the old certificate, then throw error
        final CertificateRequest certificateRequest = certificate.getCertificateRequest();
        if (signatureAttributes == null && (certificateRequest == null || certificateRequest.getSignatureAttributes() == null)) {
            throw new ValidationException(
                    ValidationError.create(
                            "Cannot find Signature Attributes in both request and old certificate"
                    )
            );
        }

        // If the key UUID is not provided and if the old certificate does not contain a key UUID, then throw error
        if (keyUuid == null && certificate.getKeyUuid() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Cannot find Key UUID in the request and old certificate"
                    )
            );
        } else if (keyUuid == null && !certificate.mapToDto().isPrivateKeyAvailability()) {
            // If the status of the private key is not valid, then throw error
            throw new ValidationException(
                    "Certificate does not have private key or private key is in incorrect state"
            );
        } else if (keyUuid != null && keyUuid.equals(certificate.getKeyUuid())) {
            throw new ValidationException(
                    ValidationError.create(
                            "Operation not permitted. Cannot use same key to rekey certificate"
                    )
            );
        } else if (keyUuid != null) {
            return keyUuid;
        } else {
            throw new ValidationException(
                    ValidationError.create(
                            "Invalid key information"
                    )
            );
        }
    }

    /**
     * Generate the CSR for new certificate for issuance and renew
     *
     * @param keyUuid             UUID of the key
     * @param tokenProfileUuid    Token profile UUID
     * @param principal           X500 Principal
     * @param signatureAttributes Signature attributes
     * @return Base64 encoded CSR string
     * @throws NotFoundException When the key or tokenProfile UUID is not found
     */
    private String generateCsr(UUID keyUuid, UUID tokenProfileUuid, X500Principal principal, List<RequestAttributeDto> signatureAttributes) throws NotFoundException {
        try {
            // Generate the CSR with the above-mentioned information
            return cryptographicOperationService.generateCsr(
                    keyUuid,
                    tokenProfileUuid,
                    principal,
                    signatureAttributes
            );
        } catch (InvalidKeySpecException | IOException | NoSuchAlgorithmException e) {
            throw new ValidationException(
                    ValidationError.create(
                            "Failed to generate the CSR. Error: " + e.getMessage()
                    )
            );
        }
    }

    /**
     * Function to validate the parameters for renewal of the certificate using the old key
     *
     * @param certificate Certificate to be renewed
     */
    private void validateRenewal(Certificate certificate) {
        if (certificate.getKey() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Certificate does not have associated key"
                    )
            );
        }
        if (certificate.mapToDto().isPrivateKeyAvailability()) {
            throw new ValidationException(
                    ValidationError.create(
                            "Private Key for the certificate is not available"
                    )
            );
        }
        if (certificate.getKey().getTokenProfile() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Token Profile associated to the key is not found"
                    )
            );
        }
        if (certificate.getKey().getTokenProfile().getTokenInstanceReference() == null
                || !certificate.getKey().getTokenProfile().getTokenInstanceReference().getStatus().equals(TokenInstanceStatus.ACTIVATED)) {
            throw new ValidationException(
                    ValidationError.create(
                            "Token Instance of the certificate is not in correct state"
                    )
            );
        }
    }

    /**
     * Function to evaluate if the certificate and the key contains the same public key
     *
     * @param certificateContent Certificate Content
     * @param csr                CSR
     */
    private void validatePublicKeyForCsrAndCertificate(String certificateContent, String csr, boolean shouldMatch) {
        try {
            X509Certificate certificate = CertificateUtil.parseCertificate(certificateContent);
            JcaPKCS10CertificationRequest csrObject = parseCsrToJcaObject(csr);
            if (shouldMatch && !Arrays.equals(certificate.getPublicKey().getEncoded(), csrObject.getPublicKey().getEncoded())) {
                throw new Exception("Public key of certificate and CSR does not match");
            }
            if (!shouldMatch && Arrays.equals(certificate.getPublicKey().getEncoded(), csrObject.getPublicKey().getEncoded())) {
                throw new Exception("Public key of certificate and CSR are same");
            }
        } catch (Exception e) {
            throw new ValidationException(
                    ValidationError.create(
                            "Unable to validate the public key of CSR and certificate. Error: " + e.getMessage()
                    )
            );
        }
    }


    private void validateSubjectDnForCertificate(String certificateContent, String csr) {
        try {
            X509Certificate certificate = CertificateUtil.parseCertificate(certificateContent);
            JcaPKCS10CertificationRequest csrObject = parseCsrToJcaObject(csr);
            if (!certificate.getSubjectX500Principal().getName().equals(csrObject.getSubject().toString())) {
                throw new Exception("Subject DN of certificate and CSR does not match");
            }
        } catch (Exception e) {
            throw new ValidationException(
                    ValidationError.create(
                            "Unable to validate the Subject DN of CSR and certificate. Error: " + e.getMessage()
                    )
            );
        }
    }

    private boolean isProtocolUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = ((CzertainlyUserDetails) authentication.getPrincipal()).getUsername();
        if (username.equals("acme") || username.equals("scep")) {
            return true;
        }
        return false;
    }

    private Map<String, Object> generateCsr(String uploadedCsr, List<RequestAttributeDto> csrAttributes, UUID keyUUid, UUID tokenProfileUuid, List<RequestAttributeDto> signatureAttributes) throws NotFoundException, CertificateException {
        String pkcs10;
        String csr;
        List<DataAttribute> merged = List.of();

        if (uploadedCsr != null && !uploadedCsr.isEmpty()) {
            csr = uploadedCsr;
        } else {
            merged = AttributeDefinitionUtils.mergeAttributes(CsrAttributes.csrAttributes(), csrAttributes);
            AttributeDefinitionUtils.validateAttributes(CsrAttributes.csrAttributes(), csrAttributes);
            csr = generateCsr(
                    keyUUid,
                    tokenProfileUuid,
                    CsrUtil.buildSubject(csrAttributes),
                    signatureAttributes
            );
        }
        try {
            pkcs10 = Base64.getEncoder().encodeToString(parseCsrToJcaObject(csr).getEncoded());
        } catch (IOException e) {
            logger.debug("Failed to parse CSR: " + e);
            throw new CertificateException(e);
        }
        return Map.of("csr", pkcs10, "attributes", merged);
    }


    private void checkNewStatus(CertificateStatus status) {
        if (status.equals(CertificateStatus.NEW)) {
            throw new ValidationException(
                    ValidationError.create("Cannot perform operation on certificate with status NEW")
            );
        }
    }

}
