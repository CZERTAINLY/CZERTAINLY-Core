package com.czertainly.core.service.impl;

import com.czertainly.api.clients.ComplianceApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.compliance.*;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.connector.compliance.ComplianceRequestRulesDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceProfilesListDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.ComplianceGroupRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRuleRepository;
import com.czertainly.core.dao.repository.ComplianceRuleRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ComplianceProfileService;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Lazy
    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private RaProfileService raProfileService;

    @Autowired
    private ComplianceApiClient complianceApiClient;

    @Autowired
    @Lazy
    private ComplianceService complianceService;

    @Autowired
    private CertificateService certificateService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.REQUEST)
    public List<ComplianceProfilesListDto> listComplianceProfiles() {
        return complianceProfileRepository.findAll().stream().map(ComplianceProfile::ListMapToDTO).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.REQUEST)
    public ComplianceProfileDto getComplianceProfile(String uuid) throws NotFoundException {
        logger.info("Requesting Compliance Profile details for: {}", uuid);
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
        logger.debug("Compliance Profile: {}", complianceProfile);
        return complianceProfile.mapToDto();
    }

    @Override
    public ComplianceProfile getComplianceProfileEntity(String uuid) throws NotFoundException {
        logger.debug("Gathering details for Entity: {}", uuid);
        return complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CREATE)
    public ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException {
        logger.info("Creating new Compliance Profile: {}", request);
        if(checkComplianceProfileEntityByName(request.getName())){
            logger.error("Compliance Profile with same name already exists");
            throw new AlreadyExistException(ComplianceProfile.class, request.getName());
        }
        ComplianceProfile complianceProfile = addComplianceEntity(request);
        logger.debug("Compliance Entity: {}", complianceProfile);
        if(request.getRules() != null && !request.getRules().isEmpty()){
            logger.info("Rules are not empty in the request. Adding them to the profile");
            addRulesForConnector(request.getRules(), complianceProfile);
        }
        return complianceProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_RULE, operation = OperationType.CREATE)
    public ComplianceProfileDto addRule(String uuid, ComplianceRuleAdditionRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException {
        logger.info("Adding new rule : {} to the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Identified profile to add rule: ", complianceProfile);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        if(isComplianceProfileRuleEntityExists(complianceProfile, request.getRuleUuid(), connector, request.getKind())){
            logger.error("Rule: {} already exists in the Compliance Profile", request);
            throw new AlreadyExistException("Selected rule is already available in the Compliance Profile");
        }
        ComplianceRule complianceRule = getComplianceRuleEntity(request.getRuleUuid(), connector, request.getKind());
        logger.debug("Rule Entity: ", complianceRule);
        ComplianceProfileRule complianceProfileRule = generateComplianceProfileRule(complianceProfile, complianceRule, request.getAttributes());
        complianceProfileRuleRepository.save(complianceProfileRule);
        return complianceProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_RULE, operation = OperationType.DELETE)
    public ComplianceProfileDto removeRule(String uuid, ComplianceRuleDeletionRequestDto request) throws NotFoundException {
        logger.info("Removing rule : {} from the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        ComplianceRule complianceRule = getComplianceRuleEntity(request.getRuleUuid(), connector, request.getKind());
        ComplianceProfileRule complianceProfileRule = complianceProfileRuleRepository.findByComplianceProfileAndComplianceRule(complianceProfile, complianceRule).orElseThrow(() -> new NotFoundException(ComplianceProfileRule.class, request.getRuleUuid()));
        complianceProfileRuleRepository.delete(complianceProfileRule);
        logger.debug("Rule: {} removed", request);
        return complianceProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_GROUP, operation = OperationType.CREATE)
    public ComplianceProfileDto addGroup(String uuid, ComplianceGroupRequestDto request) throws AlreadyExistException, NotFoundException {
        logger.info("Adding new group : {} to the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Identified profile to add group: ", complianceProfile);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        Boolean isAvail = complianceProfile.getGroups().stream().filter(r -> r.getUuid().equals(request.getGroupUuid()) && r.getConnector().getUuid().equals(request.getConnectorUuid()) && r.getKind().equals(request.getKind())).findFirst().isPresent();
        if(isAvail){
            logger.error("Group: {} already exists in the Compliance Profile", request);
            throw new AlreadyExistException("Selected group is already available in the Compliance Profile");
        }
        ComplianceGroup complianceGroup = getComplianceGroupEntity(request.getGroupUuid(), connector, request.getKind());
        complianceProfile.getGroups().add(complianceGroup);
        logger.debug("Group Entity: ", complianceGroup);
        complianceProfileRepository.save(complianceProfile);
        return complianceProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_GROUP, operation = OperationType.DELETE)
    public ComplianceProfileDto removeGroup(String uuid, ComplianceGroupRequestDto request) throws NotFoundException {
        logger.info("Removing group : {} from the profile: {}", request, uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        ComplianceGroup complianceGroup = getComplianceGroupEntity(request.getGroupUuid(), connector, request.getKind());
        complianceProfile.getGroups().remove(complianceGroup);
        logger.debug("Group: {} removed", request);
        complianceProfileRepository.save(complianceProfile);
        return complianceProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CHANGE)
    public List<SimplifiedRaProfileDto> getAssociatedRAProfiles(String uuid) throws NotFoundException {
        logger.info("Request to list RA Profiles for the profile with UUID: {}", uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Associated RA Profiles: {}", complianceProfile.getRaProfiles());
        return complianceProfile.getRaProfiles().stream().map(RaProfile::mapToDtoSimplified).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.DELETE)
    public void removeComplianceProfile(String uuid) throws NotFoundException, ValidationException {
        logger.info("Request to delete the Compliance Profile with UUID: {}", uuid);
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        logger.debug("Profile identified: ", complianceProfile);
        List<ValidationError> errors = new ArrayList<>();
        if (!complianceProfile.getRaProfiles().isEmpty()) {
            logger.warn("Compliance Profile has dependent RA Profile: {}", complianceProfile.getRaProfiles());
            errors.add(ValidationError.create("Compliance Profile {} has {} dependent RA profiles", complianceProfile.getName(),
                    complianceProfile.getRaProfiles().size()));
            complianceProfile.getRaProfiles().stream().forEach(c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!errors.isEmpty()) {
            logger.error("Unable to delete Compliance Profile due to dependency: {}", complianceProfile);
            throw new ValidationException("Could not delete Compliance Profile", errors);
        }

        complianceProfileRepository.delete(complianceProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.DELETE)
    public List<ForceDeleteMessageDto> bulkRemoveComplianceProfiles(List<String> uuids) throws ValidationException {
        logger.info("Request to remove Compliance Profiles with UUIDs: {}", String.join(",", uuids));
        List<ComplianceProfile> deletableProfiles = new ArrayList<>();
        List<ForceDeleteMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
            logger.debug("Removing profile with UUID: {}", uuid);
            List<String> errors = new ArrayList<>();
            ComplianceProfile complianceProfile;
            try {
                complianceProfile = getComplianceProfileEntityByUuid(uuid);
            } catch (NotFoundException e){
                logger.warn("Unable to find the Compliance Profile with UUID: {}, Proceeding to next", uuid);
                continue;
            }

            if (!complianceProfile.getRaProfiles().isEmpty()) {
                errors.add("RA Profiles: " + complianceProfile.getRaProfiles().size() + ". Names: ");
                complianceProfile.getRaProfiles().stream().forEach(c -> errors.add(c.getName()));
            }

            if (!errors.isEmpty()) {
                logger.warn("Dependenct RA Profiles found for: {}", complianceProfile);
                ForceDeleteMessageDto forceModal = new ForceDeleteMessageDto();
                forceModal.setUuid(complianceProfile.getUuid());
                forceModal.setName(complianceProfile.getName());
                forceModal.setMessage(String.join(",", errors));
                messages.add(forceModal);
            } else {
                deletableProfiles.add(complianceProfile);
            }
        }

        for (ComplianceProfile complianceProfile : deletableProfiles) {
            complianceProfileRepository.delete(complianceProfile);
        }
        logger.debug("Warning messages: {}", messages);
        return messages;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.FORCE_DELETE)
    public void bulkForceRemoveComplianceProfiles(List<String> uuids) {
        logger.info("Requesting force remove Compliance Profile: {}", String.join(", ", uuids));
        for (String uuid : uuids) {
            try{
                ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
                logger.debug("Trying to remove Compliance Profile: {}", complianceProfile);
                if (!complianceProfile.getRaProfiles().isEmpty()) {
                    for(RaProfile ref: complianceProfile.getRaProfiles()){
                        if(ref.getComplianceProfiles() != null) {
                            ref.getComplianceProfiles().remove(complianceProfile);
                        }
                        raProfileService.updateRaProfileEntity(ref);
                    }
                }
                complianceProfileRepository.delete(complianceProfile);
            }catch (ConnectorException e){
                logger.warn("Unable to delete the Compliance Profile with uuid {}. It may have been already deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_RULE, operation = OperationType.REQUEST)
    public List<ComplianceRulesListResponseDto> getComplianceRules(String complianceProviderUuid, String kind, List<CertificateType> certificateType) throws NotFoundException {
        logger.info("Gathering Compliance Rules for Provider: {}, Kind: {}, CertificateType: {}", complianceProviderUuid, kind, certificateType);
        List<ComplianceRulesListResponseDto> complianceRules = new ArrayList<>();
        if(certificateType == null){
            certificateType = Arrays.asList(CertificateType.class.getEnumConstants());
        }
        if(complianceProviderUuid != null && !complianceProviderUuid.isEmpty()) {
            logger.debug("Filter based on Compliance Provider: {}", complianceProviderUuid);
            Connector connector = connectorService.getConnectorEntity(complianceProviderUuid);
            logger.debug("Compliance Provider: {}", connector);
            if (kind != null && !kind.isEmpty()) {
                logger.debug("Fetching data for kind: {}", kind);
                List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, kind, certificateType);
                complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, kind));
            }
            else{
                logger.debug("Fetching data for all kinds from the connector: {}", connector);
                for(String connectorKind: connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                    logger.debug("Fetching data for Kind: {}", connectorKind);
                    List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, connectorKind, certificateType);
                    complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, connectorKind));
                }
            }
        }else{
            logger.debug("Finding rules from all available connectors in the inventory");
            for(Connector connector: connectorService.listConnectorEntityByFunctionGroup(FunctionGroupCode.COMPLIANCE_PROVIDER)){
                logger.debug("Fetching data from: {}", connector);
                if (kind != null && !kind.isEmpty()) {
                    logger.debug("Fetching data for Kind: {}", kind);
                    List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, kind, certificateType);
                    complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, kind));
                }
                else{
                    logger.debug("Fetching data for all kinds available in the connector");
                    for(String connectorKind: connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                        logger.debug("Fetching data from Kind: {}", connectorKind);
                        List<ComplianceRule> response = complianceRuleRepository.findByConnectorAndKindAndCertificateTypeIn(connector, connectorKind, certificateType);
                        complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, connectorKind));
                    }
                }
            }
        }
        logger.debug("Compliance Rules: ", complianceRules);
        return complianceRules;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_GROUP, operation = OperationType.REQUEST)
    public List<ComplianceGroupsListResponseDto> getComplianceGroups(String complianceProviderUuid, String kind) throws NotFoundException {
        logger.info("Gathering Compliance Groups for Provider: {}, Kind: {}", complianceProviderUuid, kind);
        List<ComplianceGroupsListResponseDto> complianceGroups = new ArrayList<>();
        if(complianceProviderUuid != null && !complianceProviderUuid.isEmpty()) {
            logger.debug("Filter based on Compliance Provider: {}", complianceProviderUuid);
            Connector connector = connectorService.getConnectorEntity(complianceProviderUuid);
            logger.debug("Compliance Provider: {}", connector);
            if (kind != null && !kind.isEmpty()) {
                logger.debug("Fetching data for kind: {}", kind);
                List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, kind);
                complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, kind));
            }
            else{
                logger.debug("Fetching data for all kinds from the connector: {}", connector);
                for(String connectorKind: connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                    logger.debug("Fetching data for Kind: {}", connectorKind);
                    List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, connectorKind);
                    complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, connectorKind));
                }
            }
        }else{
            logger.debug("Finding rules from all available connectors in the inventory");
            for(Connector connector: connectorService.listConnectorEntityByFunctionGroup(FunctionGroupCode.COMPLIANCE_PROVIDER)){
                logger.debug("Fetching data from: {}", connector);
                if (kind != null && !kind.isEmpty()) {
                    logger.debug("Fetching data for Kind: {}", kind);
                    List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, kind);
                    complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, kind));
                }
                else{
                    logger.debug("Fetching data for all kinds available in the connector");
                    for(String connectorKind: connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                        logger.debug("Fetching data from Kind: {}", connectorKind);
                        List<ComplianceGroup> response = complianceGroupRepository.findByConnectorAndKind(connector, connectorKind);
                        complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, connectorKind));
                    }
                }
            }
        }
        return complianceGroups;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CHANGE)
    public void associateProfile(String uuid, RaProfileAssociationRequestDto raprofile) throws NotFoundException {
        logger.info("Associate RA Profiles: {} to Compliance Profile: ", raprofile, uuid);
        for(String raProfileUuid: raprofile.getRaProfileUuids()) {
            RaProfile raProfile = raProfileService.getRaProfileEntity(raProfileUuid);
            ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
            if(raProfile.getComplianceProfiles() != null) {
                if(raProfile.getComplianceProfiles().contains(complianceProfile)) {
                    continue;
                }else {
                    raProfile.getComplianceProfiles().add(complianceProfile);
                }
            }else{
                raProfile.setComplianceProfiles(new HashSet<>(Arrays.asList(complianceProfile)));
            }
            try {
                complianceService.complianceCheckForRaProfile(raProfileUuid);
            } catch (ConnectorException e) {
                logger.error("Unable to check compliance: ", e);
            }
            raProfileService.updateRaProfileEntity(raProfile);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.COMPLIANCE_PROFILE, operation = OperationType.CHANGE)
    public void disassociateProfile(String uuid, RaProfileAssociationRequestDto raprofile) throws NotFoundException {
        logger.info("Associate RA Profiles: {} to Compliance Profile: ", raprofile, uuid);
        for(String raProfileUuid: raprofile.getRaProfileUuids()) {
            RaProfile raProfile = raProfileService.getRaProfileEntity(raProfileUuid);
            ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
            if(raProfile.getComplianceProfiles() != null) {
                if(!raProfile.getComplianceProfiles().contains(complianceProfile)) {
                    continue;
                }else {
                    raProfile.getComplianceProfiles().remove(complianceProfile);
                }
            }
            if(raProfile.getComplianceProfiles() != null || raProfile.getComplianceProfiles().isEmpty()){
                List<Certificate> certificates = certificateService.listCertificatesForRaProfile(raProfile);
                for(Certificate certificate: certificates){
                    certificate.setComplianceResult(null);
                    certificate.setComplianceStatus(null);
                    certificateService.updateCertificateEntity(certificate);
                }
            }else {
                try {
                    complianceService.complianceCheckForRaProfile(raProfileUuid);
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
        for(ComplianceProfile complianceProfile: complianceProfileRepository.findAll()){
            if(complianceProfile.getGroups().stream().map(ComplianceGroup::getConnector).collect(Collectors.toList()).contains(connector)){
                errors.add(complianceProfile.getName());
            }
        }
        //Check if the connector is being used in any of the compliance group association
        for(ComplianceProfileRule complianceProfileRule: complianceProfileRuleRepository.findAll()){
            if(complianceProfileRule.getComplianceRule().getConnector().getUuid().equals(connector.getUuid())){
                errors.add(complianceProfileRule.getComplianceProfile().getName());
            }
        }
        return errors;
    }

    @Override
    public void nullifyComplianceProviderAssociation(Connector connector) {
        //Delete all the group association for a connector
        for(ComplianceProfile complianceProfile: complianceProfileRepository.findAll()){
            if(complianceProfile.getGroups().stream().map(ComplianceGroup::getConnector).collect(Collectors.toList()).contains(connector)){
                complianceProfile.getGroups().removeAll(complianceProfile.getGroups().stream().filter(r -> r.getConnector().getUuid().equals(connector.getUuid())).collect(Collectors.toSet()));
            }
        }
        //delete all the rule association for the connector
        for(ComplianceProfileRule complianceProfileRule: complianceProfileRuleRepository.findAll()){
            if(complianceProfileRule.getComplianceRule().getConnector().getUuid().equals(connector.getUuid())){
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
    public void checkCompliance(ComplianceProfileComplianceCheckDto request) {
        for(String uuid: request.getComplianceProfileUuids()){
            try {
                complianceService.complianceCheckForComplianceProfile(uuid);
            } catch (Exception e){
                logger.error("Compliance check failed.", e);
            }
        }
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
        for (ComplianceProfileRulesRequestDto request: rules){
            Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
            List<ComplianceRule> complianceRulesFromConnector = complianceRuleRepository.findByConnectorAndKind(connector, request.getKind());
            Map<String, ComplianceRule> ruleMap = new HashMap<>();
            if(request.getRules() != null && !request.getRules().isEmpty()) {
                complianceRulesFromConnector.forEach(r -> ruleMap.put(r.getUuid(), r));
                for(ComplianceRequestRulesDto complianceRequestRulesDto: request.getRules()){
                    if(ruleMap.get(complianceRequestRulesDto.getUuid()) != null){
                        ComplianceProfileRule complianceProfileRule = generateComplianceProfileRule(profile, ruleMap.get(complianceRequestRulesDto.getUuid()), complianceRequestRulesDto.getAttributes());
                        complianceProfileRuleRepository.save(complianceProfileRule);
                    }else{
                        logger.warn("Rule with UUID {} not found from Compliance Provider", complianceRequestRulesDto.getUuid());
                    }
                }
            }
            if(request.getGroups() != null && !request.getGroups().isEmpty()) {
                for (String group : request.getGroups()) {
                    ComplianceGroup complianceGroup = complianceGroupRepository.findByUuidAndConnectorAndKind(group, connector, request.getKind()).orElseThrow(() -> new NotFoundException(ComplianceGroup.class, group));
                    profile.getGroups().add(complianceGroup);
                }
            }
        }
        complianceProfileRepository.save(profile);
    }

    private ComplianceProfileRule generateComplianceProfileRule(ComplianceProfile complianceProfile, ComplianceRule complianceRule, List<RequestAttributeDto> attributes) throws ValidationException{
        logger.debug("Generating rule for: {}, Attributes: {}", complianceRule, attributes);
        if(complianceRule.getAttributes() != null) {
            if(attributes != null) {
                AttributeDefinitionUtils.validateAttributes(complianceRule.getAttributes(), attributes);
            }else{
                throw new ValidationException("Attributes are not provided for rule with name " + complianceRule.getName());
            }
        }
        ComplianceProfileRule complianceProfileRule = new ComplianceProfileRule();
        complianceProfileRule.setComplianceProfile(complianceProfile);
        complianceProfileRule.setComplianceRule(complianceRule);
        complianceProfileRule.setAttributes(attributes);
        logger.debug("Compliance Profile Rule: {}", complianceProfileRule);
        return complianceProfileRule;
    }


    private Boolean checkComplianceProfileEntityByName(String name){
        return !complianceProfileRepository.findByName(name).isEmpty();
    }

    private ComplianceProfile getComplianceProfileEntityByUuid(String uuid) throws NotFoundException {
        return complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
    }

    private ComplianceRulesListResponseDto frameComplianceRulesResponseFromConnectorResponse(List<ComplianceRule> response, Connector connector, String kind){
        logger.error("Connector Response: {}", response);
        ComplianceRulesListResponseDto dto = new ComplianceRulesListResponseDto();
        dto.setConnectorName(connector.getName());
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(kind);
        dto.setRules(response.stream().map(ComplianceRule::mapToComplianceResponse).collect(Collectors.toList()));
        return dto;
    }

    private ComplianceGroupsListResponseDto frameComplianceGroupsResponseFromConnectorResponse(List<ComplianceGroup> response, Connector connector, String kind){
        logger.error("Connector Response: {}", response);
        ComplianceGroupsListResponseDto dto = new ComplianceGroupsListResponseDto();
        dto.setConnectorName(connector.getName());
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(kind);
        dto.setGroups(response.stream().map(ComplianceGroup::mapToGroupResponse).collect(Collectors.toList()));
        return dto;
    }

    private ComplianceRule getComplianceRuleEntity(String ruleUuid, Connector connector, String kind) throws NotFoundException{
        return complianceRuleRepository.findByUuidAndConnectorAndKind(
                        ruleUuid,
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

    private ComplianceGroup getComplianceGroupEntity(String groupUuid, Connector connector, String kind) throws NotFoundException{
        return complianceGroupRepository.findByUuidAndConnectorAndKind(
                        groupUuid,
                        connector,
                        kind)
                .orElseThrow(
                        () -> new NotFoundException(ComplianceGroup.class, groupUuid
                        )
                );
    }
}
