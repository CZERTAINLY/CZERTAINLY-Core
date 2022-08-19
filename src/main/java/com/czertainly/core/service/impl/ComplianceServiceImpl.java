package com.czertainly.core.service.impl;

import com.czertainly.api.clients.ComplianceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.compliance.ComplianceGroupsResponseDto;
import com.czertainly.api.model.connector.compliance.ComplianceRequestDto;
import com.czertainly.api.model.connector.compliance.ComplianceRequestRulesDto;
import com.czertainly.api.model.connector.compliance.ComplianceResponseDto;
import com.czertainly.api.model.connector.compliance.ComplianceResponseRulesDto;
import com.czertainly.api.model.connector.compliance.ComplianceRulesResponseDto;
import com.czertainly.api.model.core.certificate.CertificateComplianceStorageDto;
import com.czertainly.api.model.core.compliance.ComplianceConnectorAndRulesDto;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.connector.FunctionGroupDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ComplianceGroupRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRepository;
import com.czertainly.core.dao.repository.ComplianceRuleRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ComplianceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ComplianceServiceImpl implements ComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceServiceImpl.class);

    @Autowired
    private ComplianceApiClient complianceApiClient;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private ComplianceProfileRepository complianceProfileRepository;

    @Autowired
    private ComplianceGroupRepository complianceGroupRepository;

    @Autowired
    private ComplianceRuleRepository complianceRuleRepository;

    @Override
    // TODO AUTH - Secure with @ExternalAuthorization. Move to another service?
    public void addFetchGroupsAndRules(Connector connector) throws ConnectorException {
        logger.info("Fetching rules and groups for the Compliance Provider: {}", connector);
        FunctionGroupDto functionGroupDto = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        if (functionGroupDto == null) {
            logger.info("Connector: {} does not implement Compliance Provider", connector.getName());
            return;
        }
        for (String kind : functionGroupDto.getKinds()) {
            addGroups(connector, kind);
            addRules(connector, kind);
        }
    }

    @Override
    // TODO AUTH - Secure with @ExternalAuthorization. Move to another service?
    public void updateGroupsAndRules(Connector connector) throws ConnectorException {
        logger.info("Fetching the rules and groups of the Compliance Provider: {}", connector);
        FunctionGroupDto functionGroupDto = connector.mapToDto().getFunctionGroups().stream().filter(r -> r.getFunctionGroupCode().equals(FunctionGroupCode.COMPLIANCE_PROVIDER)).findFirst().orElse(null);
        if (functionGroupDto == null) {
            logger.info("Connector: {} does not implement Compliance Provider", connector.getName());
            return;
        }
        for (String kind : functionGroupDto.getKinds()) {
            updateGroups(connector, kind);
            updateRules(connector, kind);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_GROUP, action = ResourceAction.LIST)
    public Boolean complianceGroupExists(SecuredUUID uuid, Connector connector, String kind) {
        return complianceGroupRepository.findByUuidAndConnectorAndKind(uuid.toString(), connector, kind).isPresent();
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_RULE, action = ResourceAction.LIST)
    public Boolean complianceRuleExists(SecuredUUID uuid, Connector connector, String kind) {
        return complianceRuleRepository.findByUuidAndConnectorAndKind(uuid.toString(), connector, kind).isPresent();
    }

    @Override
    // TODO AUTH - make private or pass certificate UUID as method parameter instead of database entity, secure with @ExternalAuthorization
    public void checkComplianceOfCertificate(Certificate certificate) throws ConnectorException {
        logger.debug("Checking the Compliance of the Certificate: {}", certificate);
        RaProfile raProfile = certificate.getRaProfile();
        CertificateComplianceStorageDto complianceResults = new CertificateComplianceStorageDto();
        if (raProfile == null) {
            logger.warn("Certificate with uuid: {} does not have any RA Profile association", certificate.getUuid());
            return;
        }
        Set<ComplianceProfile> complianceProfiles = raProfile.getComplianceProfiles();
        if (complianceProfiles == null || complianceProfiles.isEmpty()) {
            logger.warn("Certificate with uuid: {} does not have any Compliance Profile association", certificate.getUuid());
            return;
        }
        for (ComplianceProfile complianceProfile : complianceProfiles) {
            logger.debug("Applying profile: {}", complianceProfile);
            Set<ComplianceGroup> applicableGroups = complianceProfile.getGroups();
            Map<String, List<ComplianceRulesDto>> groupRuleMap = new HashMap<>();
            for (ComplianceGroup grp : applicableGroups) {
                groupRuleMap.computeIfAbsent(grp.getConnector().getUuid(), k -> new ArrayList<>()).addAll(grp.getRules().stream().map(ComplianceRule::mapToDto).collect(Collectors.toList()));
            }

            for (ComplianceConnectorAndRulesDto connector : complianceProfile.mapToDto().getRules()) {
                logger.debug("Checking for Connector: {}", connector);
                ComplianceRequestDto complianceRequestDto = new ComplianceRequestDto();
                complianceRequestDto.setCertificate(certificate.getCertificateContent().getContent());
                List<ComplianceRulesDto> applicableRules = connector.getRules();
                if (groupRuleMap.containsKey(connector.getConnectorUuid())) {
                    applicableRules.addAll(groupRuleMap.get(connector.getConnectorUuid()));
                }
                if (applicableRules.isEmpty()) {
                    logger.debug("Compliance Profile {} does not have any rule for Connector:{}", complianceProfile.getName(), connector.getConnectorName());
                    setComplianceForCertificate(certificate.getUuid(), ComplianceStatus.NA, complianceResults);
                    return;
                }
                complianceRequestDto.setRules(getComplianceRequestRules(applicableRules));
                ComplianceResponseDto responseDto = complianceApiClient.checkCompliance(
                        getConnectorEntity(connector.getConnectorUuid()).mapToDto(),
                        connector.getKind(),
                        complianceRequestDto
                );
                logger.debug("Certificate Compliance Response from Connector: {}", responseDto);

                for (ComplianceResponseRulesDto rule : responseDto.getRules()) {
                    String ruleUuid = getComplianceRuleEntity(SecuredUUID.fromString(rule.getUuid()),
                            getConnectorEntity(connector.getConnectorUuid()), connector.getKind()).getUuid();
                    switch (rule.getStatus()) {
                        case OK:
                            complianceResults.getOk().add(ruleUuid);
                            break;
                        case NOK:
                            complianceResults.getNok().add(ruleUuid);
                            break;
                        case NA:
                            complianceResults.getNa().add(ruleUuid);
                    }
                }
                logger.debug("Status from the Connector: {}", responseDto.getStatus());
            }
        }
        ComplianceStatus overallStatus = computeOverallComplianceStatus(complianceResults);
        logger.debug("Overall Status: {}", overallStatus);
        setComplianceForCertificate(certificate.getUuid(), overallStatus, complianceResults);
    }

    @Override
    @Async
    @ExternalAuthorization(resource = Resource.RA_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void complianceCheckForRaProfile(SecuredUUID uuid) throws ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
        logger.debug("Checking compliance for all the certificates in RA Profile");
        complianceCheckForRaProfile(raProfile);
    }


    @Override
    @Async
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void complianceCheckForComplianceProfile(SecuredUUID uuid) throws ConnectorException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(
                () -> new NotFoundException(ComplianceProfile.class, uuid));
        logger.debug("Checking the compliance for all the Certificates with profile: {}", complianceProfile);
        Set<RaProfile> raProfiles = complianceProfile.getRaProfiles();
        for (RaProfile raProfile : raProfiles) {
            complianceCheckForRaProfile(raProfile);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_RULE, action = ResourceAction.DETAIL)
    // TODO AUTH - do not return DB Entity, return Dto instead, use UUIDS instead of IDs
    public List<ComplianceRule> getComplianceRuleEntityForIds(List<String> ids) {
        return complianceRuleRepository.findByUuidIn(ids);
    }

    // TODO AUTH - create separate service for ComplianceRule?
    public void saveComplianceRule(ComplianceRule complianceRule) {
        complianceRuleRepository.save(complianceRule);
    }

    private void saveComplianceGroup(ComplianceGroup complianceGroup) {
        complianceGroupRepository.save(complianceGroup);
    }

    private ComplianceGroup getComplianceGroupEntity(SecuredUUID uuid, Connector connector, String kind) throws NotFoundException {
        return complianceGroupRepository.findByUuidAndConnectorAndKind(uuid.toString(), connector, kind).orElseThrow(() -> new NotFoundException(ComplianceGroup.class, uuid));
    }

    private ComplianceRule getComplianceRuleEntity(SecuredUUID uuid, Connector connector, String kind) throws NotFoundException {
        return complianceRuleRepository.findByUuidAndConnectorAndKind(uuid.toString(), connector, kind).orElseThrow(() -> new NotFoundException(ComplianceRule.class, uuid));
    }

    private void complianceCheckForRaProfile(RaProfile raProfile) throws ConnectorException {
        List<Certificate> certificates = certificateRepository.findByRaProfile(raProfile);
        for (Certificate certificate : certificates) {
            checkComplianceOfCertificate(certificate);
        }
    }

    private void setComplianceForCertificate(String uuid, ComplianceStatus status,
                                             CertificateComplianceStorageDto result) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        certificate.setComplianceStatus(status);
        certificate.setComplianceResult(result);
    }

    private List<ComplianceRequestRulesDto> getComplianceRequestRules(List<ComplianceRulesDto> rules) {
        List<ComplianceRequestRulesDto> dtos = new ArrayList<>();
        List<String> nonDuplicateUuids = new ArrayList<>();
        for (ComplianceRulesDto rule : rules) {
            if (nonDuplicateUuids.contains(rule.getUuid())) {
                continue;
            }
            nonDuplicateUuids.add(rule.getUuid());
            ComplianceRequestRulesDto dto = new ComplianceRequestRulesDto();
            dto.setUuid(rule.getUuid());
            dto.setAttributes(rule.getAttributes());
            dtos.add(dto);
        }
        logger.debug("Compliance Rules to be validated: {}", dtos);
        return dtos;
    }

    private ComplianceStatus computeOverallComplianceStatus(CertificateComplianceStorageDto dto) {
        if (!dto.getNok().isEmpty()) {
            return ComplianceStatus.NOK;
        }
        if (dto.getNok().isEmpty() && dto.getOk().isEmpty()) {
            return ComplianceStatus.NA;
        }
        if (!dto.getOk().isEmpty()) {
            return ComplianceStatus.OK;
        }
        return ComplianceStatus.NA;
    }

    private void addGroups(Connector connector, String kind) throws ConnectorException {
        logger.info("Adding groups for the Connector: {}, Kind: {}", connector.getName(), kind);
        List<ComplianceGroupsResponseDto> groups = complianceApiClient.getComplianceGroups(connector.mapToDto(), kind);
        logger.debug("Compliance Groups: {}", groups);
        List<String> groupUuids = groups.stream().map(ComplianceGroupsResponseDto::getUuid).collect(Collectors.toList());
        if (groupUuids.size() > new HashSet<>(groupUuids).size()) {
            logger.error("Duplicate UUIDs found from the connector: UUIDs: {}", groupUuids);
            throw new ValidationException(ValidationError.create("Compliance Groups from the connector contains duplicate UUIDs. UUIDs should be unique across the groups"));
        }
        for (ComplianceGroupsResponseDto group : groups) {
            logger.debug("Saving group: {}", group);
            saveComplianceGroup(frameComplianceGroup(connector, kind, group));
        }
        logger.info("Groups for the connector: {} added", connector.getName());
    }

    private void addRules(Connector connector, String kind) throws ConnectorException {
        logger.info("Adding rules for the Connector: {}, Kind: {}", connector.getName(), kind);
        List<ComplianceRulesResponseDto> rules = complianceApiClient.getComplianceRules(connector.mapToDto(), kind, List.of());
        logger.debug("Compliance Groups: {}", rules);
        List<String> ruleUuids = rules.stream().map(ComplianceRulesResponseDto::getUuid).collect(Collectors.toList());
        if (ruleUuids.size() > new HashSet<>(ruleUuids).size()) {
            logger.error("Duplicate UUIDs found from the connector: UUIDs: {}", rules);
            throw new ValidationException(ValidationError.create("Compliance Rules from the connector contains duplicate UUIDs. UUIDs should be unique across the rules"));
        }
        for (ComplianceRulesResponseDto rule : rules) {
            logger.debug("Saving group: {}", rule);
            saveComplianceRule(frameComplianceRule(connector, kind, rule));
        }
        logger.info("Rules for the connector: {} saved", connector.getName());
    }

    private ComplianceGroup frameComplianceGroup(Connector connector, String kind, ComplianceGroupsResponseDto group) {
        ComplianceGroup complianceGroup = new ComplianceGroup();
        complianceGroup.setConnector(connector);
        complianceGroup.setDescription(group.getDescription());
        complianceGroup.setKind(kind);
        complianceGroup.setName(group.getName());
        complianceGroup.setUuid(group.getUuid());
        complianceGroup.setDecommissioned(false);
        logger.debug("Compliance Group DAO: {}", complianceGroup);
        return complianceGroup;
    }

    private ComplianceRule frameComplianceRule(Connector connector, String kind, ComplianceRulesResponseDto rule) throws NotFoundException {
        ComplianceRule complianceRule = new ComplianceRule();
        complianceRule.setConnector(connector);
        complianceRule.setDescription(rule.getDescription());
        complianceRule.setKind(kind);
        complianceRule.setName(rule.getName());
        complianceRule.setUuid(rule.getUuid());
        complianceRule.setDecommissioned(false);
        complianceRule.setCertificateType(rule.getCertificateType());
        complianceRule.setAttributes(rule.getAttributes());
        if (rule.getGroupUuid() != null && !rule.getGroupUuid().isEmpty()) {
            if (complianceGroupExists(SecuredUUID.fromString(rule.getGroupUuid()), connector, kind)) {
                complianceRule.setGroup(getComplianceGroupEntity(SecuredUUID.fromString(rule.getGroupUuid()), connector, kind));
            } else {
                logger.warn("Compliance Rule: {}, tags unknown group:{}", rule.getUuid(), rule.getGroupUuid());
            }
        }
        logger.debug("Compliance Rule DAO: {}", complianceRule);
        return complianceRule;
    }

    private void updateGroups(Connector connector, String kind) throws ConnectorException {
        logger.info("Updating Compliance Group for: {}", connector);
        List<ComplianceGroupsResponseDto> groups = complianceApiClient.getComplianceGroups(connector.mapToDto(), kind);
        List<String> groupUuids = groups.stream().map(ComplianceGroupsResponseDto::getUuid).collect(Collectors.toList());
        decommUnavailableGroups(groupUuids, connector, kind);
        updateGroups(connector, kind, groups);

    }

    private void decommUnavailableGroups(List<String> groups, Connector connector, String kind) throws NotFoundException {
        logger.info("Preparing the decommision process for the groups that are removed from connector: {}", connector);
        List<String> currentGroupsInDatabase = complianceGroupRepository.findAll().stream().map(ComplianceGroup::getUuid).collect(Collectors.toList());
        currentGroupsInDatabase.removeAll(groups);
        for (String currentGroupUuid : currentGroupsInDatabase) {
            ComplianceGroup complianceGroup = getComplianceGroupEntity(SecuredUUID.fromString(currentGroupUuid), connector, kind);
            logger.debug("Group: {} no longer available", complianceGroup);
            complianceGroup.setDecommissioned(true);
            saveComplianceGroup(complianceGroup);
        }
    }

    private void updateGroups(Connector connector, String kind, List<ComplianceGroupsResponseDto> groups) throws NotFoundException {
        for (ComplianceGroupsResponseDto group : groups) {
            if (!complianceGroupExists(SecuredUUID.fromString(group.getUuid()), connector, kind)) {
                saveComplianceGroup(frameComplianceGroup(connector, kind, group));
            } else {
                logger.debug("New group found. Adding group: {}", group);
                ComplianceGroup complianceGroup = getComplianceGroupEntity(SecuredUUID.fromString(group.getUuid()), connector, kind);
                complianceGroup.setName(group.getName());
                complianceGroup.setDescription(group.getDescription());
                saveComplianceGroup(complianceGroup);
            }
        }
    }

    private void updateRules(Connector connector, String kind) throws ConnectorException {
        logger.info("Updating Compliance Rules for: {}", connector);
        List<ComplianceRulesResponseDto> rules = complianceApiClient.getComplianceRules(connector.mapToDto(), kind, List.of());
        List<String> ruleUuids = rules.stream().map(ComplianceRulesResponseDto::getUuid).collect(Collectors.toList());
        decommUnavailableRules(ruleUuids, connector, kind);
        updateRules(connector, kind, rules);

    }

    private void decommUnavailableRules(List<String> rules, Connector connector, String kind) throws NotFoundException {
        logger.info("Preparing the decommision process for the rules that are removed from connector: {}", connector);
        List<String> currentRulesInDatabase = complianceRuleRepository.findAll().stream().map(ComplianceRule::getUuid).collect(Collectors.toList());
        currentRulesInDatabase.removeAll(rules);
        for (String currentRuleUuid : currentRulesInDatabase) {
            ComplianceRule complianceRule = getComplianceRuleEntity(SecuredUUID.fromString(currentRuleUuid), connector, kind);
            logger.debug("Rule: {} no longer available", complianceRule);
            complianceRule.setDecommissioned(true);
            saveComplianceRule(complianceRule);
        }
    }

    private void updateRules(Connector connector, String kind, List<ComplianceRulesResponseDto> rules) throws NotFoundException {
        for (ComplianceRulesResponseDto rule : rules) {
            if (!complianceRuleExists(SecuredUUID.fromString(rule.getUuid()), connector, kind)) {
                saveComplianceRule(frameComplianceRule(connector, kind, rule));
            } else {
                ComplianceRule complianceRule = getComplianceRuleEntity(SecuredUUID.fromString(rule.getUuid()), connector, kind);
                complianceRule.setName(rule.getName());
                complianceRule.setDescription(rule.getDescription());
                complianceRule.setCertificateType(rule.getCertificateType());
                if (rule.getGroupUuid() != null && !rule.getGroupUuid().isEmpty()) {
                    if (complianceGroupExists(SecuredUUID.fromString(rule.getUuid()), connector, kind)) {
                        complianceRule.setGroup(getComplianceGroupEntity(SecuredUUID.fromString(rule.getUuid()), connector, kind));
                    } else {
                        logger.warn("Compliance Rule: {}, tags unknown group:{}", rule.getUuid(), rule.getGroupUuid());
                    }
                }
                saveComplianceRule(complianceRule);
            }
        }
    }

    private Connector getConnectorEntity(String uuid) throws NotFoundException {
        return connectorRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));
    }
}
