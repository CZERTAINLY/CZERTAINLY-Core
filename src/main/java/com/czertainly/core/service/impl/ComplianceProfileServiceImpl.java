package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.compliance.*;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.connector.compliance.ComplianceRequestRulesDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceProfilesListDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.ComplianceGroupRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRuleRepository;
import com.czertainly.core.dao.repository.ComplianceRuleRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ComplianceProfileService;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ComplianceProfileServiceImpl implements ComplianceProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceProfileService.class);

    @Autowired
    private ComplianceProfileRepository complianceProfileRepository;

    @Autowired
    private ComplianceRuleRepository complianceRuleRepository;

    @Autowired
    private ComplianceProfileRuleRepository complianceProfileRuleRepository;

    @Autowired
    private ComplianceGroupRepository complianceGroupRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private RaProfileService raProfileService;

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private AttributeService attributeService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<ComplianceProfilesListDto> listComplianceProfiles(SecurityFilter filter) {
        return complianceProfileRepository.findUsingSecurityFilter(filter).stream().map(ComplianceProfile::ListMapToDTO).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public ComplianceProfileDto getComplianceProfile(SecuredUUID uuid) throws NotFoundException {
        logger.info("Requesting Compliance Profile details for: {}", uuid);
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
        logger.debug("Compliance Profile: {}", complianceProfile);
        ComplianceProfileDto dto = complianceProfile.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.COMPLIANCE_PROFILE));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public ComplianceProfile getComplianceProfileEntity(SecuredUUID uuid) throws NotFoundException {
        logger.debug("Gathering details for Entity: {}", uuid);
        return complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CREATE)
    public ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException {
        logger.info("Creating new Compliance Profile: {}", request);
        if (checkComplianceProfileEntityByName(request.getName())) {
            logger.error("Compliance Profile with same name already exists");
            throw new AlreadyExistException(ComplianceProfile.class, request.getName());
        }
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.COMPLIANCE_PROFILE);
        ComplianceProfile complianceProfile = addComplianceEntity(request);
        attributeService.createAttributeContent(complianceProfile.getUuid(), request.getCustomAttributes(), Resource.COMPLIANCE_PROFILE);
        logger.debug("Compliance Entity: {}", complianceProfile);
        if (request.getRules() != null && !request.getRules().isEmpty()) {
            logger.info("Rules are not empty in the request. Adding them to the profile");
            addRulesForConnector(request.getRules(), complianceProfile);
        }
        ComplianceProfileDto dto = complianceProfile.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(complianceProfile.getUuid(), Resource.COMPLIANCE_PROFILE));
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_RULE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public ComplianceProfileRuleDto addRule(SecuredUUID uuid, ComplianceRuleAdditionRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException {
        logger.info("Adding new rule : {} to the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Identified profile to add rule: {}", complianceProfile);
        Connector connector = getConnectorEntity(request.getConnectorUuid());
        if (isComplianceProfileRuleEntityExists(complianceProfile, request.getRuleUuid(), connector, request.getKind())) {
            logger.error("Rule: {} already exists in the Compliance Profile", request);
            throw new AlreadyExistException("Selected rule is already available in the Compliance Profile");
        }
        ComplianceRule complianceRule = getComplianceRuleEntity(request.getRuleUuid(), connector, request.getKind());
        logger.debug("Rule Entity: {}", complianceRule);
        ComplianceProfileRule complianceProfileRule = generateComplianceProfileRule(complianceProfile, complianceRule, request.getAttributes());
        complianceProfileRuleRepository.save(complianceProfileRule);
        return complianceProfileRule.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_RULE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public ComplianceProfileRuleDto removeRule(SecuredUUID uuid, ComplianceRuleDeletionRequestDto request) throws NotFoundException {
        logger.info("Removing rule : {} from the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = getConnectorEntity(request.getConnectorUuid());
        ComplianceRule complianceRule = getComplianceRuleEntity(request.getRuleUuid(), connector, request.getKind());
        ComplianceProfileRule complianceProfileRule = complianceProfileRuleRepository.findByComplianceProfileAndComplianceRule(complianceProfile, complianceRule).orElseThrow(() -> new NotFoundException(ComplianceProfileRule.class, request.getRuleUuid()));
        ComplianceProfileRuleDto response = complianceProfileRule.mapToDto();
        complianceProfileRuleRepository.delete(complianceProfileRule);
        complianceProfile.getComplianceRules().remove(complianceProfileRule);
        complianceProfileRepository.save(complianceProfile);
        logger.debug("Rule: {} removed", request);
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_GROUP, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public ComplianceProfileDto addGroup(SecuredUUID uuid, ComplianceGroupRequestDto request) throws AlreadyExistException, NotFoundException {
        logger.info("Adding new group : {} to the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Identified profile to add group: {}", complianceProfile);
        Connector connector = getConnectorEntity(request.getConnectorUuid());
        boolean isAvail = complianceProfile.getGroups().stream().anyMatch(r -> r.getUuid().toString().equals(request.getGroupUuid()) && r.getConnector().getUuid().toString().equals(request.getConnectorUuid()) && r.getKind().equals(request.getKind()));
        if (isAvail) {
            logger.error("Group: {} already exists in the Compliance Profile", request);
            throw new AlreadyExistException("Selected group is already available in the Compliance Profile");
        }
        ComplianceGroup complianceGroup = getComplianceGroupEntity(request.getGroupUuid(), connector, request.getKind());
        complianceProfile.getGroups().add(complianceGroup);
        logger.debug("Group Entity: {}", complianceGroup);
        complianceProfileRepository.save(complianceProfile);
        return complianceProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_GROUP, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public ComplianceProfileDto removeGroup(SecuredUUID uuid, ComplianceGroupRequestDto request) throws NotFoundException {
        logger.info("Removing group : {} from the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = getConnectorEntity(request.getConnectorUuid());
        ComplianceGroup complianceGroup = getComplianceGroupEntity(request.getGroupUuid(), connector, request.getKind());
        complianceProfile.getGroups().remove(complianceGroup);
        logger.debug("Group: {} removed", request);
        complianceProfileRepository.save(complianceProfile);
        return complianceProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public List<SimplifiedRaProfileDto> getAssociatedRAProfiles(SecuredUUID uuid) throws NotFoundException {
        logger.info("Request to list RA Profiles for the profile with UUID: {}", uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Associated RA Profiles: {}", complianceProfile.getRaProfiles());
        List<String> raProfileUuids = raProfileService.listRaProfiles(SecurityFilter.create(), null).stream().map(RaProfileDto::getUuid).collect(Collectors.toList());
        return complianceProfile.getRaProfiles().stream().filter(e -> raProfileUuids.contains(e.getUuid().toString())).map(RaProfile::mapToDtoSimplified).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DELETE)
    public void deleteComplianceProfile(SecuredUUID uuid) throws NotFoundException, ValidationException {
        logger.info("Request to delete the Compliance Profile with UUID: {}", uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Profile identified: {}", complianceProfile);
        deleteComplianceProfile(complianceProfile, false);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> bulkDeleteComplianceProfiles(List<SecuredUUID> uuids) throws ValidationException {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            logger.debug("Removing profile with UUID: {}", uuid);
            ComplianceProfile complianceProfile = null;
            try {
                complianceProfile = getComplianceProfileEntityByUuid(uuid);
                deleteComplianceProfile(complianceProfile, false);
            } catch (Exception e) {
                logger.warn("Unable to find the Compliance Profile with UUID: {}, Proceeding to next", uuid);
                messages.add(new BulkActionMessageDto(uuid.toString(), complianceProfile != null ? complianceProfile.getName() : "", e.getMessage()));
            }
        }
        logger.debug("Warning messages: {}", messages);
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.FORCE_DELETE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> forceDeleteComplianceProfiles(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        ComplianceProfile complianceProfile = null;
        for (SecuredUUID uuid : uuids) {
            try {
                complianceProfile = getComplianceProfileEntityByUuid(uuid);
                logger.debug("Trying to remove Compliance Profile: {}", complianceProfile);
                deleteComplianceProfile(complianceProfile, true);
            } catch (Exception e) {
                logger.warn("Unable to delete the Compliance Profile with uuid {}. It may have been already deleted", uuid);
                messages.add(new BulkActionMessageDto(uuid.toString(), complianceProfile != null ? complianceProfile.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_RULE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public List<ComplianceRulesListResponseDto> getComplianceRules(String complianceProviderUuid, String kind, List<CertificateType> certificateType) throws NotFoundException {
        logger.info("Gathering Compliance Rules for Provider: {}, Kind: {}, CertificateType: {}", complianceProviderUuid, kind, certificateType);
        List<ComplianceRulesListResponseDto> complianceRules = new ArrayList<>();
        if (certificateType == null) {
            certificateType = Arrays.asList(CertificateType.class.getEnumConstants());
        }
        if (complianceProviderUuid != null && !complianceProviderUuid.isEmpty()) {
            logger.debug("Filter based on Compliance Provider: {}", complianceProviderUuid);
            Connector connector = getConnectorEntity(complianceProviderUuid);
            logger.debug("Compliance Provider: {}", connector);
            if (kind != null && !kind.isEmpty()) {
                logger.debug("Fetching data for kind: {}", kind);
                List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, kind, certificateType);
                complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, kind));
            } else {
                logger.debug("Fetching data for all kinds from the connector: {}", connector);

                Optional<FunctionGroupDto> functionGroup = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst();
                if (functionGroup.isPresent()) {
                    for (String connectorKind : functionGroup.get().getKinds()) {
                        logger.debug("Fetching data for Kind: {}", connectorKind);
                        List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, connectorKind, certificateType);
                        complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, connectorKind));
                    }
                } else
                    logger.debug("No kinds of function group {} in the connector: {}", FunctionGroupCode.COMPLIANCE_PROVIDER, connector);
            }
        } else {
            logger.debug("Finding rules from all available connectors in the inventory");
            for (Connector connector : listComplianceProviders()) {
                logger.debug("Fetching data from: {}", connector);
                if (kind != null && !kind.isEmpty()) {
                    logger.debug("Fetching data for Kind: {}", kind);
                    List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, kind, certificateType);
                    complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, kind));
                } else {
                    logger.debug("Fetching data for all kinds available in the connector");

                    Optional<FunctionGroupDto> functionGroup = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst();
                    if (functionGroup.isPresent()) {
                        for (String connectorKind : functionGroup.get().getKinds()) {
                            logger.debug("Fetching data from Kind: {}", connectorKind);
                            List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, connectorKind, certificateType);
                            complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, connectorKind));
                        }
                    } else
                        logger.debug("No kinds of function group {} in the connector: {}", FunctionGroupCode.COMPLIANCE_PROVIDER, connector);
                }
            }
        }
        logger.debug("Compliance Rules: {}", complianceRules);
        return complianceRules;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_GROUP, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public List<ComplianceGroupsListResponseDto> getComplianceGroups(String complianceProviderUuid, String kind) throws NotFoundException {
        logger.info("Gathering Compliance Groups for Provider: {}, Kind: {}", complianceProviderUuid, kind);
        List<ComplianceGroupsListResponseDto> complianceGroups = new ArrayList<>();
        if (complianceProviderUuid != null && !complianceProviderUuid.isEmpty()) {
            logger.debug("Filter based on Compliance Provider: {}", complianceProviderUuid);
            Connector connector = getConnectorEntity(complianceProviderUuid);
            logger.debug("Compliance Provider: {}", connector);
            if (kind != null && !kind.isEmpty()) {
                logger.debug("Fetching data for kind: {}", kind);
                List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, kind);
                complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, kind));
            } else {
                logger.debug("Fetching data for all kinds from the connector: {}", connector);

                Optional<FunctionGroupDto> functionGroup = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst();
                if (functionGroup.isPresent()) {
                    for (String connectorKind : functionGroup.get().getKinds()) {
                        logger.debug("Fetching data for Kind: {}", connectorKind);
                        List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, connectorKind);
                        complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, connectorKind));
                    }
                } else
                    logger.debug("No kinds of function group {} in the connector: {}", FunctionGroupCode.COMPLIANCE_PROVIDER, connector);
            }
        } else {
            logger.debug("Finding rules from all available connectors in the inventory");
            for (Connector connector : listComplianceProviders()) {
                logger.debug("Fetching data from: {}", connector);
                if (kind != null && !kind.isEmpty()) {
                    logger.debug("Fetching data for Kind: {}", kind);
                    List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, kind);
                    complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, kind));
                } else {
                    logger.debug("Fetching data for all kinds available in the connector");

                    Optional<FunctionGroupDto> functionGroup = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst();
                    if (functionGroup.isPresent()) {
                        for (String connectorKind : functionGroup.get().getKinds()) {
                            logger.debug("Fetching data from Kind: {}", connectorKind);
                            List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, connectorKind);
                            complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, connectorKind));
                        }
                    } else
                        logger.debug("No kinds of function group {} in the connector: {}", FunctionGroupCode.COMPLIANCE_PROVIDER, connector);
                }
            }
        }
        return complianceGroups;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public void associateProfile(SecuredUUID uuid, RaProfileAssociationRequestDto raprofile) throws NotFoundException {
        logger.info("Associate RA Profiles: {} to Compliance Profile: {}", raprofile, uuid);
        for (String raProfileUuid : raprofile.getRaProfileUuids()) {
            RaProfile raProfile = raProfileService.getRaProfileEntity(SecuredUUID.fromString(raProfileUuid));
            ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
            if (raProfile.getComplianceProfiles() != null) {
                if (raProfile.getComplianceProfiles().contains(complianceProfile)) {
                    continue;
                } else {
                    raProfile.getComplianceProfiles().add(complianceProfile);
                }
            } else {
                raProfile.setComplianceProfiles(new HashSet<>(List.of(complianceProfile)));
            }
            try {
                complianceService.complianceCheckForRaProfile(SecuredUUID.fromString(raProfileUuid));
            } catch (ConnectorException e) {
                logger.error("Unable to check compliance: ", e);
            }
            raProfileService.updateRaProfileEntity(raProfile);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.UPDATE)
    public void disassociateProfile(SecuredUUID uuid, RaProfileAssociationRequestDto raprofile) throws NotFoundException {
        logger.info("Associate RA Profiles: {} to Compliance Profile: {}", raprofile, uuid);
        for (String raProfileUuid : raprofile.getRaProfileUuids()) {
            RaProfile raProfile = raProfileService.getRaProfileEntity(SecuredUUID.fromString(raProfileUuid));
            ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
            if (raProfile.getComplianceProfiles() != null) {
                if (!raProfile.getComplianceProfiles().contains(complianceProfile)) {
                    continue;
                } else {
                    raProfile.getComplianceProfiles().remove(complianceProfile);
                }
            }
            if (raProfile.getComplianceProfiles() != null || raProfile.getComplianceProfiles().isEmpty()) {
                List<Certificate> certificates = certificateService.listCertificatesForRaProfile(raProfile);
                for (Certificate certificate : certificates) {
                    certificate.setComplianceResult(null);
                    certificate.setComplianceStatus(null);
                    certificateService.updateCertificateEntity(certificate);
                }
            } else {
                try {
                    complianceService.complianceCheckForRaProfile(SecuredUUID.fromString(raProfileUuid));
                } catch (ConnectorException e) {
                    logger.error("Unable to check compliance: ", e);
                }
            }
            raProfileService.updateRaProfileEntity(raProfile);
        }
    }

    @Override
    public Set<String> isComplianceProviderAssociated(Connector connector) {
        Set<String> errors = new HashSet<>();
        //Check if the connector is being used in any of the compliance profile groups
        for (ComplianceProfile complianceProfile : complianceProfileRepository.findAll()) {
            if (complianceProfile.getGroups().stream().map(ComplianceGroup::getConnector).collect(Collectors.toList()).contains(connector)) {
                errors.add(complianceProfile.getName());
            }
        }
        //Check if the connector is being used in any of the compliance group association
        for (ComplianceProfileRule complianceProfileRule : complianceProfileRuleRepository.findAll()) {
            if (complianceProfileRule.getComplianceRule().getConnector().getUuid().equals(connector.getUuid())) {
                errors.add(complianceProfileRule.getComplianceProfile().getName());
            }
        }
        return errors;
    }

    @Override
    public void nullifyComplianceProviderAssociation(Connector connector) {
        //Delete all the group association for a connector
        for (ComplianceProfile complianceProfile : complianceProfileRepository.findAll()) {
            if (complianceProfile.getGroups().stream().map(ComplianceGroup::getConnector).collect(Collectors.toList()).contains(connector)) {
                complianceProfile.getGroups().removeAll(complianceProfile.getGroups().stream().filter(r -> r.getConnector().getUuid().equals(connector.getUuid())).collect(Collectors.toSet()));
            }
        }
        //delete all the rule association for the connector
        for (ComplianceProfileRule complianceProfileRule : complianceProfileRuleRepository.findAll()) {
            if (complianceProfileRule.getComplianceRule().getConnector().getUuid().equals(connector.getUuid())) {
                complianceProfileRuleRepository.delete(complianceProfileRule);
            }
        }
        //Delete all rules and Groups of the connector
        complianceRuleRepository.deleteAll(complianceRuleRepository.findByConnector(connector));
        complianceGroupRepository.deleteAll(complianceGroupRepository.findByConnector(connector));
    }

    @Override
    public void removeRulesAndGroupForEmptyConnector(Connector connector) {
        complianceRuleRepository.deleteAll(complianceRuleRepository.findByConnector(connector));
        complianceGroupRepository.deleteAll(complianceGroupRepository.findByConnector(connector));
    }

    @Override
    public void checkCompliance(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                complianceService.complianceCheckForComplianceProfile(uuid);
            } catch (Exception e) {
                logger.error("Compliance check failed.", e);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return complianceProfileRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(ComplianceProfile::mapToAccessControlObjects)
                .collect(Collectors.toList());
    }

    private ComplianceProfile addComplianceEntity(ComplianceProfileRequestDto request) {
        logger.debug("Adding compliance entity for: {}", request);
        ComplianceProfile complianceProfile = new ComplianceProfile();
        complianceProfile.setName(request.getName());
        complianceProfile.setDescription(request.getDescription());
        complianceProfileRepository.save(complianceProfile);
        return complianceProfile;
    }

    private void addRulesForConnector(List<ComplianceProfileRulesRequestDto> rules, ComplianceProfile profile) throws NotFoundException, ValidationException {
        for (ComplianceProfileRulesRequestDto request : rules) {
            Connector connector = getConnectorEntity(request.getConnectorUuid());
            List<ComplianceRule> complianceRulesFromConnector = complianceRuleRepository.findByConnectorAndKind(connector, request.getKind());
            Map<String, ComplianceRule> ruleMap = new HashMap<>();
            if (request.getRules() != null && !request.getRules().isEmpty()) {
                complianceRulesFromConnector.forEach(r -> ruleMap.put(r.getUuid().toString(), r));
                for (ComplianceRequestRulesDto complianceRequestRulesDto : request.getRules()) {
                    if (ruleMap.get(complianceRequestRulesDto.getUuid()) != null) {
                        ComplianceProfileRule complianceProfileRule = generateComplianceProfileRule(profile, ruleMap.get(complianceRequestRulesDto.getUuid()), complianceRequestRulesDto.getAttributes());
                        complianceProfileRuleRepository.save(complianceProfileRule);
                    } else {
                        logger.warn("Rule with UUID {} not found from Compliance Provider", complianceRequestRulesDto.getUuid());
                    }
                }
            }
            if (request.getGroups() != null && !request.getGroups().isEmpty()) {
                for (String group : request.getGroups()) {
                    ComplianceGroup complianceGroup = complianceGroupRepository.findByUuidAndConnectorAndKind(UUID.fromString(group), connector, request.getKind()).orElseThrow(() -> new NotFoundException(ComplianceGroup.class, group));
                    profile.getGroups().add(complianceGroup);
                }
            }
        }
        complianceProfileRepository.save(profile);
    }

    private ComplianceProfileRule generateComplianceProfileRule(ComplianceProfile complianceProfile, ComplianceRule complianceRule, List<RequestAttributeDto> attributes) throws ValidationException {
        logger.debug("Generating rule for: {}, Attributes: {}", complianceRule, attributes);
        if (complianceRule.getAttributes() != null) {
            if (attributes != null) {
                AttributeDefinitionUtils.validateAttributes(complianceRule.getAttributes(), attributes);
            } else {
                throw new ValidationException("Attributes are not provided for rule with name " + complianceRule.getName());
            }
        }
        ComplianceProfileRule complianceProfileRule = new ComplianceProfileRule();
        complianceProfileRule.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileRule.setComplianceProfile(complianceProfile);
        complianceProfileRule.setComplianceRule(complianceRule);
        complianceProfileRule.setAttributes(attributes);
        logger.debug("Compliance Profile Rule: {}", complianceProfileRule);
        return complianceProfileRule;
    }


    private Boolean checkComplianceProfileEntityByName(String name) {
        return complianceProfileRepository.findByName(name).isPresent();
    }

    private ComplianceProfile getComplianceProfileEntityByUuid(SecuredUUID uuid) throws NotFoundException {
        return complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
    }

    private ComplianceRulesListResponseDto frameComplianceRulesResponseFromConnectorResponse(List<ComplianceRule> response, Connector connector, String kind) {
        logger.error("Connector Response: {}", response);
        ComplianceRulesListResponseDto dto = new ComplianceRulesListResponseDto();
        dto.setConnectorName(connector.getName());
        dto.setConnectorUuid(connector.getUuid().toString());
        dto.setKind(kind);
        dto.setRules(response.stream().map(ComplianceRule::mapToComplianceResponse).collect(Collectors.toList()));
        return dto;
    }

    private ComplianceGroupsListResponseDto frameComplianceGroupsResponseFromConnectorResponse(List<ComplianceGroup> response, Connector connector, String kind) {
        logger.error("Connector Response: {}", response);
        ComplianceGroupsListResponseDto dto = new ComplianceGroupsListResponseDto();
        dto.setConnectorName(connector.getName());
        dto.setConnectorUuid(connector.getUuid().toString());
        dto.setKind(kind);
        dto.setGroups(response.stream().map(ComplianceGroup::mapToGroupResponse).collect(Collectors.toList()));
        return dto;
    }

    private ComplianceRule getComplianceRuleEntity(String ruleUuid, Connector connector, String kind) throws NotFoundException {
        return complianceRuleRepository.findByUuidAndConnectorAndKind(
                        UUID.fromString(ruleUuid),
                        connector,
                        kind)
                .orElseThrow(
                        () -> new NotFoundException(ComplianceRule.class, ruleUuid
                        )
                );
    }

    private Boolean isComplianceProfileRuleEntityExists(ComplianceProfile complianceProfile, String ruleUuid, Connector connector, String kind) throws NotFoundException {
        ComplianceRule complianceRule = getComplianceRuleEntity(ruleUuid, connector, kind);
        return complianceProfileRuleRepository.findByComplianceProfileAndComplianceRule(complianceProfile, complianceRule).isPresent();
    }

    private ComplianceGroup getComplianceGroupEntity(String groupUuid, Connector connector, String kind) throws NotFoundException {
        return complianceGroupRepository.findByUuidAndConnectorAndKind(
                        UUID.fromString(groupUuid),
                        connector,
                        kind)
                .orElseThrow(
                        () -> new NotFoundException(ComplianceGroup.class, groupUuid
                        )
                );
    }

    private Connector getConnectorEntity(String uuid) throws NotFoundException {
        return connectorRepository.findByUuid(UUID.fromString(uuid))
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));
    }

    private List<Connector> listComplianceProviders() {
        List<Connector> connectors = new ArrayList<>();

        for (Connector connector : connectorRepository.findByStatus(ConnectorStatus.CONNECTED)) {
            ConnectorDto connectorDto = connector.mapToDto();
            for (FunctionGroupDto fg : connectorDto.getFunctionGroups()) {
                if (fg.getFunctionGroupCode() == FunctionGroupCode.COMPLIANCE_PROVIDER) {
                    connectorDto.setFunctionGroups(List.of(fg));
                    connectors.add(connector);
                }
            }
        }
        return connectors;
    }

    private void deleteComplianceProfile(ComplianceProfile complianceProfile, Boolean isForce) {
        if (!isForce) {
            ValidationError error = null;
            if (!complianceProfile.getRaProfiles().isEmpty()) {
                logger.warn("Compliance Profile has dependent RA Profile: {}", complianceProfile.getRaProfiles());
                error = ValidationError.create("Dependent RA profiles: {}", complianceProfile.getRaProfiles().stream().map(RaProfile::getName).collect(Collectors.toSet()));
            }

            if (error != null) {
                logger.error("Unable to delete Compliance Profile due to dependency: {}", complianceProfile);
                throw new ValidationException(error);
            }
        }
        attributeService.deleteAttributeContent(complianceProfile.getUuid(), Resource.COMPLIANCE_PROFILE);
        complianceProfileRepository.delete(complianceProfile);
    }
}
