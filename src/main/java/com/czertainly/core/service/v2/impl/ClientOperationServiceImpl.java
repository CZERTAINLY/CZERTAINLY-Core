package com.czertainly.core.service.v2.impl;

import com.czertainly.api.clients.v2.CertificateApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.CertificateUpdateObjectsDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.v2.CertRevocationDto;
import com.czertainly.api.model.connector.v2.CertificateDataResponseDto;
import com.czertainly.api.model.connector.v2.CertificateRenewRequestDto;
import com.czertainly.api.model.connector.v2.CertificateSignRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.authority.RevocationReason;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateLocation;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.*;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.service.v2.ExtendedAttributeService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.attribute.CsrAttributes;
import com.czertainly.core.util.CsrUtil;
import com.czertainly.core.util.MetaDefinitions;
import jakarta.transaction.Transactional;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

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
    @AuditLogged(originator = ObjectType.CLIENT, affected = ObjectType.END_ENTITY_CERTIFICATE, operation = OperationType.ISSUE)
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public ClientCertificateDataResponseDto issueCertificate(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, ClientCertificateSignRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, NoSuchAlgorithmException {
        certificateService.checkIssuePermissions();
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        extendedAttributeService.validateLegacyConnector(raProfile.getAuthorityInstanceReference().getConnector());

        CertificateSignRequestDto caRequest = new CertificateSignRequestDto();
        // the CSR should be properly converted to ensure consistent Base64-encoded format
        String pkcs10;
        String csr;
        List<DataAttribute> merged = null;

        if (request.getPkcs10() != null && !request.getPkcs10().isEmpty()) {
            csr = request.getPkcs10();
        } else {
            merged = AttributeDefinitionUtils.mergeAttributes(CsrAttributes.csrAttributes(), request.getCsrAttributes());
            AttributeDefinitionUtils.validateAttributes(CsrAttributes.csrAttributes(), request.getCsrAttributes());
            csr = generateCsr(
                    request.getKeyUuid(),
                    request.getTokenProfileUuid(),
                    request.getCsrAttributes(),
                    request.getSignatureAttributes()
            );
        }
        try {
            pkcs10 = Base64.getEncoder().encodeToString(parseCsrToJcaObject(csr).getEncoded());
        } catch (IOException e) {
            logger.debug("Failed to parse CSR: " + e);
            throw new CertificateException(e);
        }
        caRequest.setPkcs10(pkcs10);
        caRequest.setAttributes(request.getAttributes());
        caRequest.setRaProfileAttributes(AttributeDefinitionUtils.getClientAttributes(raProfile.mapToDto().getAttributes()));
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.CERTIFICATE);
        CertificateDataResponseDto caResponse = certificateApiClient.issueCertificate(
                raProfile.getAuthorityInstanceReference().getConnector().mapToDto(),
                raProfile.getAuthorityInstanceReference().getAuthorityInstanceUuid(),
                caRequest);

        //Certificate certificate = certificateService.checkCreateCertificate(caResponse.getCertificateData());
        Certificate certificate = certificateService.checkCreateCertificateWithMeta(
                caResponse.getCertificateData(),
                caResponse.getMeta(),
                pkcs10,
                request.getKeyUuid(),
                merged,
                request.getSignatureAttributes()
        );

        //Create Custom Attributes
        attributeService.createAttributeContent(certificate.getUuid(), request.getCustomAttributes(), Resource.CERTIFICATE);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("CSR", pkcs10);
        certificateEventHistoryService.addEventHistory(CertificateEvent.ISSUE, CertificateEventStatus.SUCCESS, "Issued using RA Profile " + raProfile.getName(), MetaDefinitions.serialize(additionalInformation), certificate);

        logger.info("Certificate created {}", certificate);
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
    public ClientCertificateDataResponseDto renewCertificate(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, String certificateUuid, ClientCertificateRenewRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException {
        certificateService.checkRenewPermissions();
        RaProfile raProfile = raProfileRepository.findByUuidAndEnabledIsTrue(raProfileUuid.getValue())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));

        Certificate oldCertificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
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
            keyUuid = null;
        } else if (request.isUseExistingCsr()) {
            // Check if the request is for using the existing CSR
            csr = getExistingCsr(oldCertificate);
        } else {
            merged = getExistingCsrAttributes(request, oldCertificate);
            keyUuid = existingKeyValidation(request, oldCertificate);
            // Gather the signature attributes either provided in the request or get it from the old certificate
            signatureAttributes = request.getSignatureAttributes() != null
                    ? request.getSignatureAttributes()
                    : oldCertificate.getSignatureAttributes();
            csr = generateCsr(
                    keyUuid,
                    getTokenProfileUuid(request, oldCertificate),
                    AttributeDefinitionUtils.getClientAttributes(merged),
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
        caRequest.setMeta(metadataService.getMetadataWithSourceForCertificate(raProfile.getAuthorityInstanceReference().getConnectorUuid(), oldCertificate.getUuid(), Resource.CERTIFICATE, null, null));

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
                    signatureAttributes

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
            certificateEventHistoryService.addEventHistory(CertificateEvent.REVOKE, CertificateEventStatus.SUCCESS, "Certificate revoked", "", certificate);
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
        if (certificate.getCsr() == null) {
            // If the CSR is not found for the existing certificate, then throw error
            throw new ValidationException(
                    ValidationError.create(
                            "CSR does not available for the existing certificate"
                    )
            );
        }
        return certificate.getCsr();
    }

    /**
     * Check and get the CSR attributes from the existing certificate
     *
     * @param request     Certificate issuance request
     * @param certificate Existing certificate
     * @return List of attributes from the existing certificate
     */
    private List<DataAttribute> getExistingCsrAttributes(ClientCertificateRenewRequestDto request, Certificate certificate) {
        // Check if the request is for generating a new CSR.
        // If the CSR attributes are not provided in the request and of the CSR attributes are not available for the
        // existing certificate then throw error
        if (request.getCsrAttributes() == null || request.getCsrAttributes().isEmpty()) {
            if (certificate.getCsrAttributes() == null || certificate.getCsrAttributes().isEmpty()) {
                throw new ValidationException(
                        ValidationError.create(
                                "No CSR Attribute is provided. Existing CSR Attributes for the certificate is also not available"
                        )
                );
            } else {
                // If the CSR of the existing certificate is found, then use it
                return certificate.getCsrAttributes();
            }
        } else {
            // If new set of CSR attributes are found for the request, use it to create the new CSR
            AttributeDefinitionUtils.validateAttributes(CsrAttributes.csrAttributes(), request.getCsrAttributes());
            return AttributeDefinitionUtils.mergeAttributes(CsrAttributes.csrAttributes(), request.getCsrAttributes());
        }
    }

    private UUID getTokenProfileUuid(ClientCertificateRenewRequestDto request, Certificate certificate) {
        if (certificate.getKeyUuid() == null && request.getTokenProfileUuid() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Token Profile cannot be empty for creating new CSR"
                    )
            );
        }
        return certificate.getKey().getTokenProfile().getUuid();
    }

    /**
     * Validate existing key from the old certificate
     *
     * @param request     Certificate creation request
     * @param certificate Existing certificate to be renewed
     * @return UUID of the key from the old certificate
     */
    private UUID existingKeyValidation(ClientCertificateRenewRequestDto request, Certificate certificate) {
        // If the signature attributes are not provided in the request and not available in the old certificate, then throw error
        if (request.getSignatureAttributes() == null && certificate.getSignatureAttributes() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Cannot find Signature Attributes in both request and old certificate"
                    )
            );
        }

        // If the key UUID is not provided and if the old certificate does not contain a key UUID, then throw error
        if (request.getKeyUuid() == null && certificate.getKeyUuid() == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Cannot find Key UUID in the request and old certificate"
                    )
            );
        } else if (request.getKeyUuid() == null && !certificate.mapToDto().isPrivateKeyAvailability()) {
            // If the status of the private key is not valid, then throw error
            throw new ValidationException(
                    "Certificate does not have private key or private key is in incorrect state"
            );
        } else {
            return request.getKeyUuid() != null ? request.getKeyUuid() : certificate.getKeyUuid();
        }
    }

    /**
     * Generate the CSR for new certificate for issuance and renew
     *
     * @param keyUuid             UUID of the key
     * @param tokenProfileUuid    Token profile UUID
     * @param csrAttributes       CSR attributes
     * @param signatureAttributes Signature attributes
     * @return Base64 encoded CSR string
     * @throws NotFoundException When the key or tokenProfile UUID is not found
     */
    private String generateCsr(UUID keyUuid, UUID tokenProfileUuid, List<RequestAttributeDto> csrAttributes, List<RequestAttributeDto> signatureAttributes) throws NotFoundException {
        try {
            // Generate the CSR with the above-mentioned information
            return cryptographicOperationService.generateCsr(
                    keyUuid,
                    tokenProfileUuid,
                    csrAttributes,
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

}
