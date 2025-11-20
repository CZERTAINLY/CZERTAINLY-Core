package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileRelationDto;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.compliance.SimplifiedComplianceProfileDto;
import com.czertainly.api.model.client.raprofile.*;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttributeV2;
import com.czertainly.api.model.connector.authority.CaCertificatesRequestDto;
import com.czertainly.api.model.connector.authority.CaCertificatesResponseDto;
import com.czertainly.api.model.connector.v2.CertificateDataResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.api.model.core.raprofile.RaProfileCertificateValidationSettingsUpdateDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.entity.scep.ScepProfile;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.cmp.CmpProfileRepository;
import com.czertainly.core.dao.repository.scep.ScepProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.v2.ComplianceProfileService;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.PermissionEvaluator;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.service.model.SecuredList;
import com.czertainly.core.service.v2.ExtendedAttributeService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.ValidatorUtil;
import com.czertainly.core.util.X509ObjectToString;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;

@Service(Resource.Codes.RA_PROFILE)
@Transactional
public class RaProfileServiceImpl implements RaProfileService {

    private static final Logger logger = LoggerFactory.getLogger(RaProfileServiceImpl.class);

    private RaProfileRepository raProfileRepository;
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    private AuthorityInstanceApiClient authorityInstanceApiClient;
    private CertificateRepository certificateRepository;
    private AcmeProfileRepository acmeProfileRepository;
    private ExtendedAttributeService extendedAttributeService;
    private ComplianceService complianceService;
    private ComplianceProfileService complianceProfileService;
    private AttributeEngine attributeEngine;
    private PermissionEvaluator permissionEvaluator;
    private RaProfileProtocolAttributeRepository raProfileProtocolAttributeRepository;
    private ScepProfileRepository scepProfileRepository;
    private CmpProfileRepository cmpProfileRepository;
    private ApprovalProfileRelationRepository approvalProfileRelationRepository;
    private ApprovalProfileRepository approvalProfileRepository;
    private CertificateContentRepository certificateContentRepository;

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.LIST, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.LIST)
    public List<RaProfileDto> listRaProfiles(SecurityFilter filter, Optional<Boolean> enabled) {
        filter.setParentRefProperty("authorityInstanceReferenceUuid");
        return enabled.map(isEnabled -> raProfileRepository.findUsingSecurityFilter(filter, isEnabled).stream().map(RaProfile::mapToDtoSimple).toList()).orElseGet(() -> raProfileRepository.findUsingSecurityFilter(filter).stream().map(RaProfile::mapToDtoSimple).toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.LIST)
    public SecuredList<RaProfile> listRaProfilesAssociatedWithAcmeProfile(String acmeProfileUuid, SecurityFilter filter) {
        List<RaProfile> raProfiles = raProfileRepository.findAllByAcmeProfileUuid(UUID.fromString(acmeProfileUuid));
        return SecuredList.fromFilter(filter, raProfiles);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.CREATE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public RaProfileDto addRaProfile(SecuredParentUUID authorityInstanceUuid, AddRaProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException, NotFoundException {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException(ValidationError.create("RA profile name must not be empty"));
        }

        Optional<RaProfile> o = raProfileRepository.findByName(request.getName());
        if (o.isPresent()) {
            throw new AlreadyExistException(RaProfile.class, request.getName());
        }

        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(authorityInstanceUuid).orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, authorityInstanceUuid));
        attributeEngine.validateCustomAttributesContent(Resource.RA_PROFILE, request.getCustomAttributes());
        mergeAndValidateAttributes(authorityInstanceRef, request.getAttributes());

        RaProfile raProfile = createRaProfile(request, authorityInstanceRef);
        raProfileRepository.save(raProfile);

        setAuthorityCertificates(authorityInstanceRef, raProfile);

        RaProfileDto raProfileDto = raProfile.mapToDto();
        raProfileDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.RA_PROFILE, raProfile.getUuid(), request.getCustomAttributes()));
        raProfileDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(authorityInstanceRef.getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid(), request.getAttributes()));

        return raProfileDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL)
    public RaProfileDto getRaProfile(SecuredUUID uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
        RaProfileDto dto = raProfile.mapToDto();
        if (raProfile.getAuthorityInstanceReference() != null && raProfile.getAuthorityInstanceReference().getConnectorUuid() != null) {
            dto.setAttributes(attributeEngine.getObjectDataAttributesContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid()));
        }
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.RA_PROFILE, raProfile.getUuid()));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public RaProfileDto getRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        RaProfileDto dto = raProfile.mapToDto();
        if (raProfile.getAuthorityInstanceReference() != null && raProfile.getAuthorityInstanceReference().getConnectorUuid() != null) {
            dto.setAttributes(attributeEngine.getObjectDataAttributesContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid()));
        }
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.RA_PROFILE, raProfile.getUuid()));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public RaProfileDto editRaProfile(SecuredParentUUID authorityInstanceUuid, SecuredUUID uuid, EditRaProfileRequestDto request) throws ConnectorException, AttributeException, NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(authorityInstanceUuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, authorityInstanceUuid));

        attributeEngine.validateCustomAttributesContent(Resource.RA_PROFILE, request.getCustomAttributes());
        mergeAndValidateAttributes(authorityInstanceRef, request.getAttributes());

        updateRaProfile(raProfile, authorityInstanceRef, request);
        raProfileRepository.save(raProfile);

        RaProfileDto raProfileDto = raProfile.mapToDto();
        raProfileDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.RA_PROFILE, raProfile.getUuid(), request.getCustomAttributes()));
        raProfileDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(authorityInstanceRef.getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid(), request.getAttributes()));

        return raProfileDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public RaProfileDto updateRaProfileValidationConfiguration(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, RaProfileCertificateValidationSettingsUpdateDto request) throws NotFoundException {
        RaProfile raProfile = getRaProfileEntity(raProfileUuid);
        raProfile.setValidationEnabled(request.getEnabled());
        if (Boolean.TRUE.equals(request.getEnabled())) {
            raProfile.setValidationFrequency(request.getFrequency());
            raProfile.setExpiringThreshold(request.getExpiringThreshold());
        } else {
            raProfile.setValidationFrequency(null);
            raProfile.setExpiringThreshold(null);
        }
        raProfileRepository.save(raProfile);
        return raProfile.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DELETE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public void deleteRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        deleteRaProfileInt(uuid);
    }


    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DELETE)
    public void deleteRaProfile(SecuredUUID uuid) throws NotFoundException {
        deleteRaProfileInt(uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public void enableRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        RaProfile entity = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        entity.setEnabled(true);
        raProfileRepository.save(entity);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public void disableRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        RaProfile entity = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        entity.setEnabled(false);
        raProfileRepository.save(entity);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DELETE)
    public void bulkDeleteRaProfile(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                deleteRaProfile(uuid);
            } catch (NotFoundException e) {
                logger.warn("Unable to find RA Profile with uuid {}. It may have already been deleted", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ENABLE)
    public void bulkDisableRaProfile(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                RaProfile entity = raProfileRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

                entity.setEnabled(false);
                raProfileRepository.save(entity);
            } catch (NotFoundException e) {
                logger.warn("Unable to disable RA Profile with uuid {}. It may have been deleted", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ENABLE)
    public void bulkEnableRaProfile(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                RaProfile entity = raProfileRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

                entity.setEnabled(true);
                raProfileRepository.save(entity);
            } catch (NotFoundException e) {
                logger.warn("Unable to enable RA Profile with uuid {}. It may have been deleted", uuids);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE)
    public void bulkRemoveAssociatedAcmeProfile(List<SecuredUUID> uuids) {
        List<RaProfile> raProfiles = raProfileRepository.findAllByUuidIn(
                uuids.stream().map(SecuredUUID::getValue).toList());
        raProfiles.forEach(raProfile -> raProfile.setAcmeProfile(null));
        raProfileRepository.saveAll(raProfiles);
    }

    @Override
    public void bulkRemoveAssociatedScepProfile(List<SecuredUUID> uuids) {
        List<RaProfile> raProfiles = raProfileRepository.findAllByUuidIn(
                uuids.stream().map(SecuredUUID::getValue).toList());
        raProfiles.forEach(raProfile -> raProfile.setScepProfile(null));
        raProfileRepository.saveAll(raProfiles);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    // TODO - use acme service to obtain ACME profile
    public RaProfileAcmeDetailResponseDto getAcmeForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        return getRaProfileEntity(uuid).mapToAcmeDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid, SecuredUUID acmeProfileUuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException, AttributeException, NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        if (ValidatorUtil.containsUnreservedCharacters(raProfile.getName())) {
            throw new ValidationException(ValidationError.create("RA Profile name can contain only unreserved URI characters (alphanumeric, hyphen, period, underscore, and tilde)"));
        }
        if (raProfile.getAuthorityInstanceReference() == null || raProfile.getAuthorityInstanceReference().getConnectorUuid() == null) {
            throw new ValidationException(ValidationError.create("Cannot activate ACME protocol for RA profile without associated authority and its connector"));
        }
        AcmeProfile acmeProfile = acmeProfileRepository.findByUuid(acmeProfileUuid).orElseThrow(() -> new NotFoundException(AcmeProfile.class, acmeProfileUuid));

        extendedAttributeService.mergeAndValidateIssueAttributes(raProfile, request.getIssueCertificateAttributes());
        extendedAttributeService.mergeAndValidateRevokeAttributes(raProfile, request.getRevokeCertificateAttributes());

        RaProfileProtocolAttribute raProfileProtocolAttribute = raProfile.getProtocolAttribute();
        raProfileProtocolAttribute.setAcmeIssueCertificateAttributes(AttributeDefinitionUtils.serialize(attributeEngine.getDataAttributesV2ByContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), request.getIssueCertificateAttributes())));
        raProfileProtocolAttribute.setAcmeRevokeCertificateAttributes(AttributeDefinitionUtils.serialize(attributeEngine.getDataAttributesV2ByContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), request.getRevokeCertificateAttributes())));
        raProfileProtocolAttribute.setRaProfile(raProfile);
        raProfileProtocolAttributeRepository.save(raProfileProtocolAttribute);

        raProfile.setAcmeProfile(acmeProfile);
        raProfile.setProtocolAttribute(raProfileProtocolAttribute);
        raProfileRepository.save(raProfile);

        return raProfile.mapToAcmeDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public void deactivateAcmeForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        raProfile.setAcmeProfile(null);
        RaProfileProtocolAttribute raProfileProtocolAttribute = raProfile.getProtocolAttribute();
        raProfileProtocolAttribute.setAcmeRevokeCertificateAttributes(null);
        raProfileProtocolAttribute.setAcmeIssueCertificateAttributes(null);
        raProfile.setProtocolAttribute(raProfileProtocolAttribute);
        raProfileRepository.save(raProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public RaProfileScepDetailResponseDto activateScepForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid, SecuredUUID scepProfileUuid, ActivateScepForRaProfileRequestDto request) throws ConnectorException, ValidationException, AttributeException, NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        ScepProfile scepProfile = scepProfileRepository.findByUuid(scepProfileUuid).orElseThrow(() -> new NotFoundException(ScepProfile.class, scepProfileUuid));

        if (raProfile.getAuthorityInstanceReference() == null || raProfile.getAuthorityInstanceReference().getConnectorUuid() == null) {
            throw new ValidationException(ValidationError.create("Cannot activate SCEP protocol for RA profile without associated authority and its connector"));
        }

        extendedAttributeService.mergeAndValidateIssueAttributes(raProfile, request.getIssueCertificateAttributes());

        RaProfileProtocolAttribute raProfileProtocolAttribute = raProfile.getProtocolAttribute();
        raProfileProtocolAttribute.setScepIssueCertificateAttributes(AttributeDefinitionUtils.serialize(attributeEngine.getDataAttributesV2ByContent(raProfile.getAuthorityInstanceReference().getConnectorUuid(), request.getIssueCertificateAttributes())));
        raProfileProtocolAttribute.setRaProfile(raProfile);
        raProfileProtocolAttributeRepository.save(raProfileProtocolAttribute);

        raProfile.setScepProfile(scepProfile);
        raProfile.setProtocolAttribute(raProfileProtocolAttribute);
        raProfileRepository.save(raProfile);

        return raProfile.mapToScepDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public void deactivateScepForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        raProfile.setScepProfile(null);
        RaProfileProtocolAttribute raProfileProtocolAttribute = raProfile.getProtocolAttribute();
        raProfileProtocolAttribute.setScepIssueCertificateAttributes(null);
        raProfile.setProtocolAttribute(raProfileProtocolAttribute);
        raProfileRepository.save(raProfile);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------
    // CMP protocol
    // -----------------------------------------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    @ExternalAuthorization(
            resource = Resource.RA_PROFILE,
            action = ResourceAction.DETAIL,
            parentResource = Resource.AUTHORITY,
            parentAction = ResourceAction.DETAIL
    )
    public RaProfileCmpDetailResponseDto getCmpForRaProfile(
            SecuredParentUUID authorityInstanceUuid,
            SecuredUUID raProfileUuid
    ) throws NotFoundException {
        return getRaProfileEntity(raProfileUuid).mapToCmpDto();
    }

    @Override
    @ExternalAuthorization(
            resource = Resource.RA_PROFILE,
            action = ResourceAction.UPDATE,
            parentResource = Resource.AUTHORITY,
            parentAction = ResourceAction.DETAIL
    )
    public RaProfileCmpDetailResponseDto activateCmpForRaProfile(
            SecuredParentUUID authorityUuid,
            SecuredUUID uuid,
            SecuredUUID cmpProfileUuid,
            ActivateCmpForRaProfileRequestDto request
    ) throws ConnectorException, ValidationException, AttributeException, NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        CmpProfile cmpProfile = cmpProfileRepository.findByUuid(cmpProfileUuid)
                .orElseThrow(() -> new NotFoundException(CmpProfile.class, cmpProfileUuid));

        if (raProfile.getAuthorityInstanceReference() == null || raProfile.getAuthorityInstanceReference().getConnectorUuid() == null) {
            throw new ValidationException(ValidationError.create("Cannot activate CMP protocol for RA profile without associated authority and its connector"));
        }

        extendedAttributeService.mergeAndValidateIssueAttributes(raProfile, request.getIssueCertificateAttributes());
        extendedAttributeService.mergeAndValidateRevokeAttributes(raProfile, request.getRevokeCertificateAttributes());

        RaProfileProtocolAttribute raProfileProtocolAttribute = raProfile.getProtocolAttribute();
        raProfileProtocolAttribute.setCmpIssueCertificateAttributes(
                AttributeDefinitionUtils.serialize(
                        attributeEngine.getDataAttributesV2ByContent(
                                raProfile.getAuthorityInstanceReference().getConnectorUuid(),
                                request.getIssueCertificateAttributes())
                )
        );
        raProfileProtocolAttribute.setCmpRevokeCertificateAttributes(
                AttributeDefinitionUtils.serialize(
                        attributeEngine.getDataAttributesV2ByContent(
                                raProfile.getAuthorityInstanceReference().getConnectorUuid(),
                                request.getRevokeCertificateAttributes())
                )
        );
        raProfileProtocolAttribute.setRaProfile(raProfile);
        raProfileProtocolAttributeRepository.save(raProfileProtocolAttribute);

        raProfile.setCmpProfile(cmpProfile);
        raProfile.setProtocolAttribute(raProfileProtocolAttribute);
        raProfileRepository.save(raProfile);

        return raProfile.mapToCmpDto();
    }

    @Override
    @ExternalAuthorization(
            resource = Resource.RA_PROFILE,
            action = ResourceAction.UPDATE,
            parentResource = Resource.AUTHORITY,
            parentAction = ResourceAction.DETAIL
    )
    public void deactivateCmpForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        raProfile.setCmpProfile(null);
        RaProfileProtocolAttribute raProfileProtocolAttribute = raProfile.getProtocolAttribute();
        raProfileProtocolAttribute.setCmpRevokeCertificateAttributes(null);
        raProfileProtocolAttribute.setCmpIssueCertificateAttributes(null);
        raProfile.setProtocolAttribute(raProfileProtocolAttribute);
        raProfileRepository.save(raProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.LIST)
    public SecuredList<RaProfile> listRaProfilesAssociatedWithCmpProfile(String cmpProfileUuid, SecurityFilter filter) {
        List<RaProfile> raProfiles = raProfileRepository.findAllByCmpProfileUuid(UUID.fromString(cmpProfileUuid));
        return SecuredList.fromFilter(filter, raProfiles);
    }

    @Override
    public void bulkRemoveAssociatedCmpProfile(List<SecuredUUID> uuids) {
        List<RaProfile> raProfiles = raProfileRepository.findAllByUuidIn(
                uuids.stream().map(SecuredUUID::getValue).toList());
        raProfiles.forEach(raProfile -> raProfile.setCmpProfile(null));
        raProfileRepository.saveAll(raProfiles);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ANY, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.ANY)
    public List<BaseAttributeV2> listRevokeCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        return extendedAttributeService.listRevokeCertificateAttributes(raProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.ANY, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.ANY)
    public List<BaseAttributeV2> listIssueCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        return extendedAttributeService.listIssueCertificateAttributes(raProfile);
    }

    @Override
    // TODO - remove, service should not allow modifying RaProfile entity outside of it.
    public RaProfile updateRaProfileEntity(RaProfile raProfile) {
        raProfileRepository.save(raProfile);
        return raProfile;
    }

    @Override
    @Async
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkCompliance(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            logger.info("Checking compliance for RA Profile: {}", uuid);
            try {
                complianceService.checkResourceObjectCompliance(Resource.RA_PROFILE, uuid.getValue());
            } catch (Exception e) {
                logger.error("Compliance check failed.", e);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.LIST, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.LIST)
    public Long statisticsRaProfilesCount(SecurityFilter filter) {
        filter.setParentRefProperty("authorityInstanceReferenceUuid");
        return raProfileRepository.countUsingSecurityFilter(filter, null);
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<SimplifiedComplianceProfileDto> getComplianceProfiles(String authorityUuid, String raProfileUuid, SecurityFilter filter) throws NotFoundException {
        //Evaluate RA profile permissions
        ((RaProfileService) AopContext.currentProxy()).getRaProfile(SecuredParentUUID.fromString(authorityUuid), SecuredUUID.fromString(raProfileUuid));
        return complianceProfileService.getAssociatedComplianceProfiles(Resource.RA_PROFILE, UUID.fromString(raProfileUuid)).stream().map(cp -> {
            SimplifiedComplianceProfileDto dto = new SimplifiedComplianceProfileDto();
            dto.setUuid(cp.getUuid().toString());
            dto.setName(cp.getName());
            dto.setDescription(cp.getDescription());
            return dto;
        }).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DETAIL, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.MEMBERS)
    public void evaluateCertificateRaProfilePermissions(SecuredUUID certificateUuid, SecuredParentUUID raProfileUuid) {
    }

    @Override
    public SecuredList<RaProfile> listRaProfilesAssociatedWithScepProfile(String scepProfileUuid, SecurityFilter filter) {
        List<RaProfile> raProfiles = raProfileRepository.findAllByScepProfileUuid(UUID.fromString(scepProfileUuid));
        return SecuredList.fromFilter(filter, raProfiles);
    }

    @Override
    public RaProfileScepDetailResponseDto getScepForRaProfile(SecuredParentUUID authorityInstanceUuid, SecuredUUID raProfileUuid) throws NotFoundException {
        return getRaProfileEntity(raProfileUuid).mapToScepDto();
    }


    @Override
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        return raProfileRepository.findResourceObject(objectUuid, RaProfile_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return raProfileRepository.listResourceObjects(filter, RaProfile_.name);
    }

    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL)
    // TODO - make private, service should not allow modifying RaProfile entity outside of it.
    public RaProfile getRaProfileEntity(SecuredUUID uuid) throws NotFoundException {
        return raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        RaProfile profile = getRaProfileEntity(uuid);
        if (profile.getAuthorityInstanceReference() == null) {
            return;
        }
        // Parent Permission evaluation - Authority Instance
        permissionEvaluator.authorityInstance(profile.getAuthorityInstanceReference().getSecuredUuid());

    }

    @Override
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.LIST)
    public List<ApprovalProfileDto> getAssociatedApprovalProfiles(final String authorityInstanceUuid, final String raProfileUuid, final SecurityFilter securityFilter) throws NotFoundException {
        //Evaluate RA profile permissions
        ((RaProfileService) AopContext.currentProxy()).getRaProfile(SecuredParentUUID.fromString(authorityInstanceUuid), SecuredUUID.fromString(raProfileUuid));

        final TriFunction<Root<ApprovalProfileRelation>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause = (root, cb, cr) -> {
            final Predicate resourcePredicate = cb.equal(root.get("resource"), Resource.RA_PROFILE);
            final Predicate resourceUuidPredicate = cb.equal(root.get("resourceUuid"), UUID.fromString(raProfileUuid));
            return cb.and(resourcePredicate, resourceUuidPredicate);
        };

        final List<ApprovalProfileRelation> approvalProfileRelations = approvalProfileRelationRepository.findUsingSecurityFilter(securityFilter, List.of("approvalProfile"), additionalWhereClause);
        return approvalProfileRelations.stream().map(apr -> apr.getApprovalProfile().getTheLatestApprovalProfileVersion().mapToDto()).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.DETAIL)
    public ApprovalProfileRelationDto associateApprovalProfile(String authorityInstanceUuid, String raProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException {
        //Evaluate RA profile permissions
        ((RaProfileService) AopContext.currentProxy()).getRaProfile(SecuredParentUUID.fromString(authorityInstanceUuid), SecuredUUID.fromString(raProfileUuid));

        final Optional<ApprovalProfile> approvalProfileOptional = approvalProfileRepository.findByUuid(approvalProfileUuid);
        if (approvalProfileOptional.isEmpty()) {
            throw new NotFoundException("There is no such approval profile: " + approvalProfileUuid.getValue());
        }

        final Optional<List<ApprovalProfileRelation>> approvalProfileRelationsOptional = approvalProfileRelationRepository.findByResourceUuid(UUID.fromString(raProfileUuid));
        if (approvalProfileRelationsOptional.isPresent() && !approvalProfileRelationsOptional.get().isEmpty()) {
            throw new ValidationException(ValidationError.create("There is not possible to have more than ONE associated approval profile for the RA profile uuid: " + raProfileUuid));
        }

        final ApprovalProfileRelation approvalProfileRelation = new ApprovalProfileRelation();
        approvalProfileRelation.setApprovalProfile(approvalProfileOptional.get());
        approvalProfileRelation.setApprovalProfileUuid(approvalProfileOptional.get().getUuid());
        approvalProfileRelation.setResource(Resource.RA_PROFILE);
        approvalProfileRelation.setResourceUuid(UUID.fromString(raProfileUuid));
        approvalProfileRelationRepository.save(approvalProfileRelation);

        logger.info("There was registered new relation between Approval profile {} and RA profile {}", approvalProfileUuid.getValue(), raProfileUuid);

        return approvalProfileRelation.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.DETAIL)
    public void disassociateApprovalProfile(String authorityInstanceUuid, String raProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException {
        //Evaluate RA profile permissions
        ((RaProfileService) AopContext.currentProxy()).getRaProfile(SecuredParentUUID.fromString(authorityInstanceUuid), SecuredUUID.fromString(raProfileUuid));

        final TriFunction<Root<ApprovalProfileRelation>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause = (root, cb, cr) -> {
            final Predicate resourcePredicate = cb.equal(root.get("resource"), Resource.RA_PROFILE);
            final Predicate resourceUuidPredicate = cb.equal(root.get("resourceUuid"), UUID.fromString(raProfileUuid));
            final Predicate approvalProfileUuidPredicate = cb.equal(root.get("approvalProfileUuid"), approvalProfileUuid.getValue());
            return cb.and(resourcePredicate, resourceUuidPredicate, approvalProfileUuidPredicate);
        };

        final List<ApprovalProfileRelation> approvalProfileRelations = approvalProfileRelationRepository.findUsingSecurityFilter(SecurityFilter.create(), List.of("approvalProfile"), additionalWhereClause);

        if (approvalProfileRelations == null || approvalProfileRelations.isEmpty()) {
            throw new NotFoundException("There is no such approval profile (" + approvalProfileUuid.getValue() + ") associated with Ra profile (" + raProfileUuid + ")");
        }

        approvalProfileRelations.stream().forEach(approvalProfileRelation -> logger.info("There will be removed approval profile {} from ra profile {}", approvalProfileRelation.getApprovalProfileUuid(), approvalProfileRelation.getResourceUuid()));
        approvalProfileRelationRepository.deleteAll(approvalProfileRelations);
    }

    @Override
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.AUTHORITY, parentAction = ResourceAction.DETAIL)
    public List<CertificateDetailDto> getAuthorityCertificateChain(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid) throws ConnectorException, NotFoundException {
        RaProfile raProfile = getRaProfileEntity(raProfileUuid);
        AuthorityInstanceReference authorityInstanceReference = authorityInstanceReferenceRepository.findByUuid(authorityUuid)
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, authorityUuid));

        List<RequestAttributeDto> requestAttributes = attributeEngine.getRequestObjectDataAttributesContent(authorityInstanceReference.getConnectorUuid(), null, Resource.RA_PROFILE, raProfile.getUuid());
        CaCertificatesResponseDto caCertificatesResponseDto = authorityInstanceApiClient.getCaCertificates(authorityInstanceReference.getConnector().mapToDto(), authorityInstanceReference.getAuthorityInstanceUuid(), new CaCertificatesRequestDto(requestAttributes));
        List<CertificateDataResponseDto> certificateDataResponseDtos = caCertificatesResponseDto.getCertificates();
        List<CertificateDetailDto> certificateDetailDtos = new ArrayList<>();
        for (CertificateDataResponseDto certificateDataResponseDto : certificateDataResponseDtos) {
            X509Certificate certificate;
            String fingerprint;
            try {
                certificate = CertificateUtil.parseCertificate(certificateDataResponseDto.getCertificateData());
                fingerprint = CertificateUtil.getThumbprint(certificate);
            } catch (java.security.cert.CertificateException | NoSuchAlgorithmException e) {
                logger.warn("Cannot process certificate from CA certificate chain returned from authority of RA profile {}", raProfile.getName());
                break;
            }

            Optional<Certificate> existingCertificate = certificateRepository.findByFingerprint(fingerprint);
            if (existingCertificate.isPresent()) {
                certificateDetailDtos.add(existingCertificate.get().mapToDto());
            } else {
                Certificate modal = new Certificate();
                CertificateUtil.prepareIssuedCertificate(modal, certificate);
                CertificateContent certificateContent = certificateContentRepository.findByFingerprint(fingerprint);
                if (certificateContent == null) {
                    certificateContent = new CertificateContent();
                    certificateContent.setContent(CertificateUtil.normalizeCertificateContent(X509ObjectToString.toPem(certificate)));
                    certificateContent.setFingerprint(fingerprint);
                    certificateContentRepository.save(certificateContent);
                }
                modal.setFingerprint(fingerprint);
                modal.setCertificateContent(certificateContent);
                modal.setCertificateContentId(certificateContent.getId());
                certificateRepository.save(modal);
                certificateDetailDtos.add(modal.mapToDto());
            }
        }
        return certificateDetailDtos;
    }

    private void mergeAndValidateAttributes(AuthorityInstanceReference authorityInstanceRef, List<RequestAttributeDto> attributes) throws ConnectorException, AttributeException {
        logger.debug("Merging and validating attributes on authority instance {}. Request Attributes are: {}", authorityInstanceRef, attributes);
        if (authorityInstanceRef.getConnector() == null) {
            throw new ValidationException(ValidationError.create("Connector of the Authority is not available / deleted"));
        }

        ConnectorDto connectorDto = authorityInstanceRef.getConnector().mapToDto();

        // validate first by connector
        if (Boolean.FALSE.equals(authorityInstanceApiClient.validateRAProfileAttributes(connectorDto, authorityInstanceRef.getAuthorityInstanceUuid(), attributes))) {
            throw new ValidationException(ValidationError.create("RA profile attributes validation failed."));
        }

        // list definitions
        List<BaseAttributeV2> definitions = authorityInstanceApiClient.listRAProfileAttributes(connectorDto, authorityInstanceRef.getAuthorityInstanceUuid());

        // validate and update definitions with attribute engine
        attributeEngine.validateUpdateDataAttributes(authorityInstanceRef.getConnectorUuid(), null, definitions, attributes);
    }

    private RaProfile createRaProfile(AddRaProfileRequestDto dto, AuthorityInstanceReference authorityInstanceRef) {
        RaProfile entity = new RaProfile();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAuthorityInstanceReference(authorityInstanceRef);
        entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        entity.setAuthorityInstanceName(authorityInstanceRef.getName());
        return entity;
    }

    private void updateRaProfile(RaProfile entity, AuthorityInstanceReference authorityInstanceRef, EditRaProfileRequestDto dto) {
        entity.setDescription(dto.getDescription());
        entity.setAuthorityInstanceReference(authorityInstanceRef);
        if (dto.isEnabled() != null) {
            entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        }
        entity.setAuthorityInstanceName(authorityInstanceRef.getName());

        setAuthorityCertificates(authorityInstanceRef, entity);
    }

    private void deleteRaProfileInt(SecuredUUID uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
        List<AcmeProfile> acmeProfiles = acmeProfileRepository.findByRaProfile(raProfile);
        for (AcmeProfile acmeProfile : acmeProfiles) {
            acmeProfile.setRaProfile(null);
            acmeProfile.setRaProfileUuid(null);
            acmeProfileRepository.save(acmeProfile);
        }
        for (Certificate certificate : certificateRepository.findByRaProfile(raProfile)) {
            certificate.setRaProfile(null);
            certificate.setRaProfileUuid(null);
            certificateRepository.save(certificate);
        }

        attributeEngine.deleteAllObjectAttributeContent(Resource.RA_PROFILE, raProfile.getUuid());
        raProfileRepository.delete(raProfile);
    }

    private void setAuthorityCertificates(AuthorityInstanceReference authorityInstanceRef, RaProfile raProfile) {
        try {
            List<CertificateDetailDto> certificateChain = getAuthorityCertificateChain(SecuredParentUUID.fromUUID(authorityInstanceRef.getUuid()), SecuredUUID.fromUUID(raProfile.getUuid()));
            raProfile.setAuthorityCertificateUuid(certificateChain.isEmpty() ? null : UUID.fromString(certificateChain.get(0).getUuid()));
        } catch (NotFoundException ignored) {
            // exception ignored since get CA certs from connector is optional
            logger.debug("CA certificate chain not implemented for connector {}: {}",
                    authorityInstanceRef.getConnector().getName(), authorityInstanceRef.getConnector().getUuid());
        } catch (Exception e) {
            logger.warn("CA certificate chain from RA profile authority could not be retrieved: {}", e.getMessage());
        }
    }

    // SETTERs

    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) {
        this.raProfileRepository = raProfileRepository;
    }

    @Autowired
    public void setAuthorityInstanceReferenceRepository(AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository) {
        this.authorityInstanceReferenceRepository = authorityInstanceReferenceRepository;
    }

    @Autowired
    public void setAuthorityInstanceApiClient(AuthorityInstanceApiClient authorityInstanceApiClient) {
        this.authorityInstanceApiClient = authorityInstanceApiClient;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }


    @Autowired
    public void setAcmeProfileRepository(AcmeProfileRepository acmeProfileRepository) {
        this.acmeProfileRepository = acmeProfileRepository;
    }

    @Autowired
    public void setExtendedAttributeService(ExtendedAttributeService extendedAttributeService) {
        this.extendedAttributeService = extendedAttributeService;
    }

    @Autowired
    public void setComplianceService(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @Autowired
    public void setComplianceProfileService(ComplianceProfileService complianceProfileService) {
        this.complianceProfileService = complianceProfileService;
    }

    @Autowired
    public void setPermissionEvaluator(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Autowired
    public void setRaProfileProtocolAttributeRepository(RaProfileProtocolAttributeRepository raProfileProtocolAttributeRepository) {
        this.raProfileProtocolAttributeRepository = raProfileProtocolAttributeRepository;
    }

    @Autowired
    public void setScepProfileRepository(ScepProfileRepository scepProfileRepository) {
        this.scepProfileRepository = scepProfileRepository;
    }

    @Autowired
    public void setCmpProfileRepository(CmpProfileRepository cmpProfileRepository) {
        this.cmpProfileRepository = cmpProfileRepository;
    }

    @Autowired
    public void setApprovalProfileRelationRepository(ApprovalProfileRelationRepository approvalProfileRelationRepository) {
        this.approvalProfileRelationRepository = approvalProfileRelationRepository;
    }

    @Autowired
    public void setApprovalProfileRepository(ApprovalProfileRepository approvalProfileRepository) {
        this.approvalProfileRepository = approvalProfileRepository;
    }

    @Autowired
    public void setCertificateContentRepository(CertificateContentRepository certificateContentRepository) {
        this.certificateContentRepository = certificateContentRepository;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }
}
