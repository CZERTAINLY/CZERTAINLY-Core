package com.czertainly.core.service.impl;

import com.czertainly.api.clients.ComplianceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.compliance.*;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.connector.compliance.ComplianceGroupsResponseDto;
import com.czertainly.api.model.connector.compliance.ComplianceRequestRulesDto;
import com.czertainly.api.model.connector.compliance.ComplianceRulesResponseDto;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceProfilesListDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.ComplianceGroupRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRuleRepository;
import com.czertainly.core.dao.repository.ComplianceRuleRepository;
import com.czertainly.core.service.ComplianceProfileService;
import com.czertainly.core.service.ConnectorService;

import com.czertainly.core.service.RaProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
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
    private ConnectorService connectorService;

    @Autowired
    private RaProfileService raProfileService;

    @Autowired
    private ComplianceApiClient complianceApiClient;

    @Override
    public List<ComplianceProfilesListDto> listComplianceProfiles() {
        return complianceProfileRepository.findAll().stream().map(ComplianceProfile::ListMapToDTO).collect(Collectors.toList());
    }

    @Override
    public ComplianceProfileDto getComplianceProfile(String uuid) throws NotFoundException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
        return complianceProfile.mapToDto();
    }

    @Override
    public ComplianceProfile getComplianceProfileEntity(String uuid) throws NotFoundException {
        return complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
    }

    @Override
    public ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, ConnectorException {
        if(checkComplianceProfileEntityByName(request.getName())){
            throw new AlreadyExistException(ComplianceProfile.class, request.getName());
        }
        ComplianceProfile complianceProfile = addComplianceEntity(request);
        if(request.getRules() != null && !request.getRules().isEmpty()){
            addRulesForConnector(request.getRules(), complianceProfile);
        }
        return complianceProfile.mapToDto();
    }

    @Override
    public void addRule(String uuid, ComplianceRuleAdditionRequestDto request) throws AlreadyExistException, NotFoundException {
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        if(isComplianceProfileRuleEntityExists(complianceProfile, request.getRuleUuid(), connector, request.getKind())){
            throw new AlreadyExistException("Selected rule is already available in the Compliance Profile");
        }
        ComplianceRule complianceRule = getComplianceRuleEntity(request.getRuleUuid(), connector, request.getKind());
        ComplianceProfileRule complianceProfileRule = generateComplianceProfileRule(complianceProfile, complianceRule, request.getAttributes());
        complianceProfileRuleRepository.save(complianceProfileRule);
    }

    @Override
    public void removeRule(String uuid, ComplianceRuleDeletionRequestDto request) throws NotFoundException {
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        ComplianceRule complianceRule = getComplianceRuleEntity(request.getRuleUuid(), connector, request.getKind());
        ComplianceProfileRule complianceProfileRule = complianceProfileRuleRepository.findByComplianceProfileAndComplianceRule(complianceProfile, complianceRule).orElseThrow(() -> new NotFoundException(ComplianceProfileRule.class, request.getRuleUuid()));
        complianceProfileRuleRepository.delete(complianceProfileRule);
    }

    @Override
    public void addGroup(String uuid, ComplianceGroupRequestDto request) throws AlreadyExistException, NotFoundException {
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        Boolean isAvail = complianceProfile.getComplianceGroups().stream().filter(r -> r.getUuid() == request.getGroupUuid() && r.getConnector().getUuid() == request.getConnectorUuid() && r.getKind() == request.getKind()).findFirst().isPresent();
        if(isAvail){
            throw new AlreadyExistException("Selected group is already available in the Compliance Profile");
        }
        ComplianceGroup complianceGroup = getComplianceGroupEntity(request.getGroupUuid(), connector, request.getKind());
        complianceProfile.getComplianceGroups().add(complianceGroup);
        complianceProfileRepository.save(complianceProfile);
    }

    @Override
    public void removeGroup(String uuid, ComplianceGroupRequestDto request) throws NotFoundException {
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
        ComplianceGroup complianceGroup = getComplianceGroupEntity(request.getGroupUuid(), connector, request.getKind());
        complianceProfile.getComplianceGroups().remove(complianceGroup);
        complianceProfileRepository.save(complianceProfile);
    }

    @Override
    public List<SimplifiedRaProfileDto> getAssociatedRAProfiles(String uuid) throws NotFoundException {
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
        return complianceProfile.getRaProfiles().stream().map(RaProfile::mapToDtoSimplified).collect(Collectors.toList());
    }

    @Override
    public void removeComplianceProfile(String uuid) throws NotFoundException, ValidationException {
        ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);

        List<ValidationError> errors = new ArrayList<>();
        if (!complianceProfile.getRaProfiles().isEmpty()) {
            errors.add(ValidationError.create("Compliance Profile {} has {} dependent RA profiles", complianceProfile.getName(),
                    complianceProfile.getRaProfiles().size()));
            complianceProfile.getRaProfiles().stream().forEach(c -> errors.add(ValidationError.create(c.getName())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete Compliance Profile", errors);
        }

        complianceProfileRepository.delete(complianceProfile);
    }

    @Override
    public List<ForceDeleteMessageDto> bulkRemoveComplianceProfiles(List<String> uuids) throws ValidationException {
        List<ComplianceProfile> deletableProfiles = new ArrayList<>();
        List<ForceDeleteMessageDto> messages = new ArrayList<>();
        for (String uuid : uuids) {
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
        return messages;
    }

    @Override
    public void bulkForceRemoveComplianceProfiles(List<String> uuids) {
        for (String uuid : uuids) {
            try{
                ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
                if (!complianceProfile.getRaProfiles().isEmpty()) {
                    for(RaProfile ref: complianceProfile.getRaProfiles()){
                        ref.setComplianceProfile(null);
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
    public List<ComplianceRulesListResponseDto> getComplianceRules(String complianceProviderUuid, String kind, List<String> certificateType) throws ConnectorException {
        List<ComplianceRulesListResponseDto> complianceRules = new ArrayList<>();
        if(complianceProviderUuid != null && !complianceProviderUuid.isEmpty()) {
            ConnectorDto connector = connectorService.getConnector(complianceProviderUuid);
            if (kind != null && !kind.isEmpty()) {
                List<ComplianceRulesResponseDto> response = complianceApiClient.getComplianceRules(connector, kind, certificateType);
                complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, kind));
            }
            else{
                for(String connectorKind: connector.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                    List<ComplianceRulesResponseDto> response = complianceApiClient.getComplianceRules(connector, connectorKind, certificateType);
                    complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, connectorKind));
                }
            }
        }else{
            for(ConnectorDto connector: connectorService.listConnectorsByFunctionGroup(FunctionGroupCode.COMPLIANCE_PROVIDER)){
                if (kind != null && !kind.isEmpty()) {
                    List<ComplianceRulesResponseDto> response = complianceApiClient.getComplianceRules(connector, kind, certificateType);
                    complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, kind));
                }
                else{
                    for(String connectorKind: connector.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                        List<ComplianceRulesResponseDto> response = complianceApiClient.getComplianceRules(connector, connectorKind, certificateType);
                        complianceRules.add(frameComplianceRulesResponseFromConnectorResponse(response, connector, connectorKind));
                    }
                }
            }
        }
        return complianceRules;
    }

    @Override
    public List<ComplianceGroupsListResponseDto> getComplianceGroups(String complianceProviderUuid, String kind) throws ConnectorException {
        List<ComplianceGroupsListResponseDto> complianceGroups = new ArrayList<>();
        if(complianceProviderUuid != null && !complianceProviderUuid.isEmpty()) {
            ConnectorDto connector = connectorService.getConnector(complianceProviderUuid);
            if (kind != null && !kind.isEmpty()) {
                List<ComplianceGroupsResponseDto> response = complianceApiClient.getComplianceGroups(connector, kind);
                complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, kind));
            }
            else{
                for(String connectorKind: connector.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                    List<ComplianceGroupsResponseDto> response = complianceApiClient.getComplianceGroups(connector, connectorKind);
                    complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, connectorKind));
                }
            }
        }else{
            for(ConnectorDto connector: connectorService.listConnectorsByFunctionGroup(FunctionGroupCode.COMPLIANCE_PROVIDER)){
                if (kind != null && !kind.isEmpty()) {
                    List<ComplianceGroupsResponseDto> response = complianceApiClient.getComplianceGroups(connector, kind);
                    complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, kind));
                }
                else{
                    for(String connectorKind: connector.getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().get().getKinds()){
                        List<ComplianceGroupsResponseDto> response = complianceApiClient.getComplianceGroups(connector, connectorKind);
                        complianceGroups.add(frameComplianceGroupsResponseFromConnectorResponse(response, connector, connectorKind));
                    }
                }
            }
        }
        return complianceGroups;
    }

    @Override
    public void associateProfile(String uuid, RaProfileAssociationRequestDto raprofile) throws NotFoundException {
        for(String raProfileUuid: raprofile.getRaProfileUuids()) {
            RaProfile raProfile = raProfileService.getRaProfileEntity(raProfileUuid);
            ComplianceProfile complianceProfile = getComplianceProfileEntityByUuid(uuid);
            raProfile.setComplianceProfile(complianceProfile);
            raProfileService.updateRaProfileEntity(raProfile);
        }
    }

    private ComplianceProfile addComplianceEntity(ComplianceProfileRequestDto request) {
        ComplianceProfile complianceProfile = new ComplianceProfile();
        complianceProfile.setName(request.getName());
        complianceProfile.setDescription(request.getDescription());
        complianceProfileRepository.save(complianceProfile);
        return complianceProfile;
    }

    private void addRulesForConnector(List<ComplianceProfileRulesRequestDto> rules, ComplianceProfile profile) throws ConnectorException {
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
                    List<ComplianceRulesResponseDto> connectorGroupRules = complianceApiClient.getComplianceGroupRules(connector.mapToDto(), request.getKind(), group);
                    for(ComplianceRulesResponseDto complianceRequestRulesDto: connectorGroupRules) {
                        ComplianceGroup complianceGroup = getComplianceGroupEntity(complianceRequestRulesDto.getUuid(), connector, request.getKind());
                        profile.getComplianceGroups().add(complianceGroup);
                    }
                }
            }
        }
        complianceProfileRepository.save(profile);
    }

    private ComplianceProfileRule generateComplianceProfileRule(ComplianceProfile complianceProfile, ComplianceRule complianceRule, List<RequestAttributeDto> attributes){
        ComplianceProfileRule complianceProfileRule = new ComplianceProfileRule();
        complianceProfileRule.setComplianceProfile(complianceProfile);
        complianceProfileRule.setComplianceRule(complianceRule);
        complianceProfileRule.setAttributes(attributes);
        return complianceProfileRule;
    }


    private Boolean checkComplianceProfileEntityByName(String name){
        return !complianceProfileRepository.findByName(name).isEmpty();
    }

    private ComplianceProfile getComplianceProfileEntityByUuid(String uuid) throws NotFoundException {
        return complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
    }

    private ComplianceRulesListResponseDto frameComplianceRulesResponseFromConnectorResponse(List<ComplianceRulesResponseDto> response, ConnectorDto connector, String kind){
        ComplianceRulesListResponseDto dto = new ComplianceRulesListResponseDto();
        dto.setConnectorName(connector.getName());
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(kind);
        dto.setRules(response);
        return dto;
    }

    private ComplianceGroupsListResponseDto frameComplianceGroupsResponseFromConnectorResponse(List<ComplianceGroupsResponseDto> response, ConnectorDto connector, String kind){
        ComplianceGroupsListResponseDto dto = new ComplianceGroupsListResponseDto();
        dto.setConnectorName(connector.getName());
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(kind);
        dto.setGroups(response);
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
