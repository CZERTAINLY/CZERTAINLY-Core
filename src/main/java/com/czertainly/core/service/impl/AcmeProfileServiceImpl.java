package com.czertainly.core.service.impl;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AcmeProfileService;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.service.model.SecuredList;
import com.czertainly.core.service.v2.ExtendedAttributeService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcmeProfileServiceImpl implements AcmeProfileService {

    private static final Logger logger = LoggerFactory.getLogger(AcmeProfileServiceImpl.class);
    private static final String NONE_CONSTANT = "NONE";

    private final AcmeProfileRepository acmeProfileRepository;
    private RaProfileService raProfileService;
    private ExtendedAttributeService extendedAttributeService;

    @Autowired
    public AcmeProfileServiceImpl(AcmeProfileRepository acmeProfileRepository) {
        this.acmeProfileRepository = acmeProfileRepository;
    }

    @Autowired
    public void setRaProfileService(RaProfileService raProfileRepository) {
        this.raProfileService = raProfileRepository;
    }

    @Autowired
    public void setExtendedAttributeService(ExtendedAttributeService extendedAttributeService) {
        this.extendedAttributeService = extendedAttributeService;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.LIST)
    public List<AcmeProfileListDto> listAcmeProfile(SecurityFilter filter) {
        logger.debug("Getting all the ACME Profiles available in the database");
        return acmeProfileRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(AcmeProfile::mapToDtoSimple)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.DETAIL)
    public AcmeProfileDto getAcmeProfile(SecuredUUID uuid) throws NotFoundException {
        logger.info("Requesting the details for the ACME Profile with uuid " + uuid);
        return getAcmeProfileEntity(uuid).mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.CREATE)
    public AcmeProfileDto createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        if (request.getName() == null || request.getName().isEmpty()) {
            throw new ValidationException(ValidationError.create("Name cannot be empty"));
        }
        if (request.getValidity() != null && request.getValidity() < 0) {
            throw new ValidationException(ValidationError.create("Order Validity cannot be less than 0"));
        }
        if (request.getRetryInterval() != null && request.getRetryInterval() < 0) {
            throw new ValidationException(ValidationError.create("Retry Interval cannot be less than 0"));
        }
        logger.info("Creating a new ACME Profile");

        if (acmeProfileRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("ACME Profile with same name already exists");
        }

        AcmeProfile acmeProfile = new AcmeProfile();
        acmeProfile.setEnabled(false);
        acmeProfile.setName(request.getName());
        acmeProfile.setDescription(request.getDescription());
        acmeProfile.setDnsResolverIp(request.getDnsResolverIp());
        acmeProfile.setDnsResolverPort(request.getDnsResolverPort());
        acmeProfile.setRetryInterval(Optional.ofNullable(request.getRetryInterval()).orElse(36000));
        acmeProfile.setValidity(Optional.ofNullable(request.getValidity()).orElse(30));
        acmeProfile.setWebsite(request.getWebsiteUrl());
        acmeProfile.setTermsOfServiceUrl(request.getTermsOfServiceUrl());
        acmeProfile.setRequireContact(request.isRequireContact());
        acmeProfile.setRequireTermsOfService(request.isRequireTermsOfService());
        acmeProfile.setDisableNewOrders(false);

        if (request.getRaProfileUuid() != null && !request.getRaProfileUuid().isEmpty() && !request.getRaProfileUuid().equals(NONE_CONSTANT)) {
            RaProfile raProfile = getRaProfile(request.getRaProfileUuid());
            acmeProfile.setRaProfile(raProfile);
            acmeProfile.setIssueCertificateAttributes(AttributeDefinitionUtils.serialize(extendedAttributeService.mergeAndValidateIssueAttributes(raProfile, request.getIssueCertificateAttributes())));
            acmeProfile.setRevokeCertificateAttributes(AttributeDefinitionUtils.serialize(extendedAttributeService.mergeAndValidateRevokeAttributes(raProfile, request.getRevokeCertificateAttributes())));
        }
        acmeProfileRepository.save(acmeProfile);
        return acmeProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.UPDATE)
    public AcmeProfileDto editAcmeProfile(SecuredUUID uuid, AcmeProfileEditRequestDto request) throws ConnectorException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        if (request.isRequireContact() != null) {
            acmeProfile.setRequireContact(request.isRequireContact());
        }
        if (request.isRequireTermsOfService() != null) {
            acmeProfile.setRequireTermsOfService(request.isRequireTermsOfService());
        }
        if (request.isTermsOfServiceChangeDisable() != null) {
            acmeProfile.setDisableNewOrders(request.isTermsOfServiceChangeDisable());
        }
        if (request.getRaProfileUuid() != null) {
            if (request.getRaProfileUuid().equals(NONE_CONSTANT)) {
                acmeProfile.setRaProfile(null);
            } else {
                RaProfile raProfile = getRaProfile(request.getRaProfileUuid());
                acmeProfile.setRaProfile(raProfile);
                acmeProfile.setIssueCertificateAttributes(AttributeDefinitionUtils.serialize(extendedAttributeService.mergeAndValidateIssueAttributes(raProfile, request.getIssueCertificateAttributes())));
                acmeProfile.setRevokeCertificateAttributes(AttributeDefinitionUtils.serialize(extendedAttributeService.mergeAndValidateRevokeAttributes(raProfile, request.getRevokeCertificateAttributes())));
            }
        }
        if (request.getDescription() != null) {
            acmeProfile.setDescription(request.getDescription());
        }
        if (request.getDnsResolverIp() != null) {
            acmeProfile.setDnsResolverIp(request.getDnsResolverIp());
        }
        if (request.getDnsResolverPort() != null) {
            acmeProfile.setDnsResolverPort(request.getDnsResolverPort());
        }
        if (request.getRetryInterval() != null) {
            if (request.getRetryInterval() < 0) {
                throw new ValidationException(ValidationError.create("Retry Interval cannot be less than 0"));
            }
            acmeProfile.setRetryInterval(request.getRetryInterval());
        }
        if (request.getValidity() != null) {
            if (request.getValidity() < 0) {
                throw new ValidationException(ValidationError.create("Order Validity cannot be less than 0"));
            }
            acmeProfile.setValidity(request.getValidity());
        }
        if (request.getTermsOfServiceUrl() != null) {
            acmeProfile.setTermsOfServiceUrl(request.getTermsOfServiceUrl());
        }
        if (request.getWebsiteUrl() != null) {
            acmeProfile.setWebsite(request.getWebsiteUrl());
        }
        if (request.isTermsOfServiceChangeDisable() != null) {
            acmeProfile.setDisableNewOrders(request.isTermsOfServiceChangeDisable());
        }
        if (request.getTermsOfServiceChangeUrl() != null) {
            acmeProfile.setTermsOfServiceChangeUrl(request.getTermsOfServiceChangeUrl());
        }
        acmeProfileRepository.save(acmeProfile);
        return acmeProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.DELETE)
    public void deleteAcmeProfile(SecuredUUID uuid) throws NotFoundException, ValidationException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        deleteAcmeProfile(acmeProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.ENABLE)
    public void enableAcmeProfile(SecuredUUID uuid) throws NotFoundException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        if (acmeProfile.isEnabled() != null && acmeProfile.isEnabled()) {
            throw new RuntimeException("ACME Profile is already enabled");
        }
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.ENABLE)
    public void disableAcmeProfile(SecuredUUID uuid) throws NotFoundException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        if (!acmeProfile.isEnabled()) {
            throw new RuntimeException("ACME Profile is already disabled");
        }
        acmeProfile.setEnabled(false);
        acmeProfileRepository.save(acmeProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.ENABLE)
    public void bulkEnableAcmeProfile(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
                if (acmeProfile.isEnabled()) {
                    logger.warn("ACME Profile is already enabled");
                }
                acmeProfile.setEnabled(true);
                acmeProfileRepository.save(acmeProfile);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.ENABLE)
    public void bulkDisableAcmeProfile(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
                if (acmeProfile.isEnabled() != null && acmeProfile.isEnabled()) {
                    logger.warn("ACME Profile is already disabled");
                }
                acmeProfile.setEnabled(false);
                acmeProfileRepository.save(acmeProfile);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.DELETE)
    public List<BulkActionMessageDto> bulkDeleteAcmeProfile(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            AcmeProfile acmeProfile = null;
            try {
                acmeProfile = getAcmeProfileEntity(uuid);
                deleteAcmeProfile(acmeProfile);
            } catch (Exception e) {
                logger.error(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), acmeProfile != null ? acmeProfile.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.ACME_PROFILE, action = ResourceAction.UPDATE)
    public void updateRaProfile(SecuredUUID uuid, String raProfileUuid) throws NotFoundException {
        AcmeProfile acmeProfile = getAcmeProfileEntity(uuid);
        acmeProfile.setRaProfile(getRaProfile(raProfileUuid));
        acmeProfileRepository.save(acmeProfile);
    }

    @Override
    public List<BulkActionMessageDto> bulkForceRemoveACMEProfiles(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            AcmeProfile acmeProfile = null;
            try {
                acmeProfile = getAcmeProfileEntity(uuid);
                SecuredList<RaProfile> raProfiles = raProfileService.listRaProfilesAssociatedWithAcmeProfile(acmeProfile.getUuid(), SecurityFilter.create());
                // TODO AUTH - if there is forbidden ra profile in the ra profile list, the operation will be denied. Other possibility is to unassign
                // acme profile only from allowed ones, but that would make the forbidden ra profiles point to nonexistent acme profile.
                raProfileService.bulkRemoveAssociatedAcmeProfile(raProfiles.getAll().stream().map(UniquelyIdentifiedAndAudited::getSecuredUuid).collect(Collectors.toList()));
                deleteAcmeProfile(acmeProfile);
            } catch (Exception e) {
                logger.warn(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), acmeProfile != null ? acmeProfile.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    private RaProfile getRaProfile(String uuid) throws NotFoundException {
        return raProfileService.getRaProfileEntity(SecuredUUID.fromString(uuid));
    }

    private AcmeProfile getAcmeProfileEntity(SecuredUUID uuid) throws NotFoundException {
        return acmeProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(AcmeProfile.class, uuid));
    }

    private void deleteAcmeProfile(AcmeProfile acmeProfile) {
        SecuredList<RaProfile> raProfiles = raProfileService.listRaProfilesAssociatedWithAcmeProfile(acmeProfile.getUuid(), SecurityFilter.create());
        if (!raProfiles.isEmpty()) {
            throw new ValidationException(
                    ValidationError.create(
                            String.format(
                                    "Dependent RA Profiles (%d): %s",
                                    raProfiles.size(),
                                    raProfiles.getAllowed().stream().map(RaProfile::getName).collect(Collectors.joining(","))
                            )
                    )
            );
        } else {
            acmeProfileRepository.delete(acmeProfile);
        }
    }
}
