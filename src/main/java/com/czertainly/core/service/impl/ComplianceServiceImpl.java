package com.czertainly.core.service.impl;

import com.czertainly.api.clients.ComplianceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.compliance.ComplianceRequestDto;
import com.czertainly.api.model.connector.compliance.ComplianceRequestRulesDto;
import com.czertainly.api.model.connector.compliance.ComplianceResponseDto;
import com.czertainly.api.model.connector.compliance.ComplianceResponseRulesDto;
import com.czertainly.api.model.core.certificate.CertificateComplianceStorageDto;
import com.czertainly.api.model.core.compliance.ComplianceConnectorAndRulesDto;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.ComplianceGroupRepository;
import com.czertainly.core.dao.repository.ComplianceRuleRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ComplianceProfileService;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.core.service.RaProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
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
    private ConnectorService connectorService;

    @Lazy
    @Autowired
    private CertificateService certificateService;

    @Autowired
    private RaProfileService raProfileService;

    @Autowired
    private ComplianceProfileService complianceProfileService;

    @Lazy
    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ComplianceGroupRepository complianceGroupRepository;

    @Autowired
    private ComplianceRuleRepository complianceRuleRepository;

    @Override
    public ComplianceGroup getComplianceGroupEntity(String uuid, Connector connector, String kind) throws NotFoundException {
        return complianceGroupRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).orElseThrow(() -> new NotFoundException(ComplianceGroup.class, uuid));
    }

    @Override
    public Boolean complianceGroupExists(String uuid, Connector connector, String kind) {
        return complianceGroupRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).isPresent();
    }

    @Override
    public ComplianceRule getComplianceRuleEntity(String uuid, Connector connector, String kind) throws NotFoundException {
        return complianceRuleRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).orElseThrow(() -> new NotFoundException(ComplianceRule.class, uuid));
    }

    @Override
    public Boolean complianceRuleExists(String uuid, Connector connector, String kind) {
        return complianceRuleRepository.findByUuidAndConnectorAndKind(uuid, connector, kind).isPresent();
    }

    @Override
    public void saveComplianceGroup(ComplianceGroup complianceGroup) {
        complianceGroupRepository.save(complianceGroup);
    }

    @Override
    public void saveComplianceRule(ComplianceRule complianceRule) {
        complianceRuleRepository.save(complianceRule);
    }

    @Override
    public void checkComplianceOfCertificate(Certificate certificate) throws ConnectorException {
        logger.debug("Checking the Compliance of the Certificate: {}", certificate);
        RaProfile raProfile = certificate.getRaProfile();
        CertificateComplianceStorageDto complianceResults = new CertificateComplianceStorageDto();
        List<ComplianceStatus> allStatuses = new ArrayList<>();
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
            for(ComplianceGroup grp: applicableGroups){
                groupRuleMap.computeIfAbsent(grp.getConnector().getUuid(), k -> new ArrayList<>()).addAll(grp.getRules().stream().map(ComplianceRule::mapToDto).collect(Collectors.toList()));
            }

            for (ComplianceConnectorAndRulesDto connector : complianceProfile.mapToDto().getRules()) {
                logger.debug("Checking for Connector: {}", connector);
                ComplianceRequestDto complianceRequestDto = new ComplianceRequestDto();
                complianceRequestDto.setCertificate(certificate.getCertificateContent().getContent());
                List<ComplianceRulesDto> applicableRules = connector.getRules();
                if(groupRuleMap.containsKey(connector.getConnectorUuid())){
                    applicableRules.addAll(groupRuleMap.get(connector.getConnectorUuid()));
                }
                if(applicableRules.isEmpty()){
                    allStatuses.add(ComplianceStatus.NA);
                    logger.debug("Compliance Profile {} does not have any rule for Connector:{}", complianceProfile.getName(), connector.getConnectorName());
                }
                complianceRequestDto.setRules(getComplianceRequestRules(applicableRules));
                ComplianceResponseDto responseDto = complianceApiClient.checkCompliance(
                        connectorService.getConnectorEntity(connector.getConnectorUuid()).mapToDto(),
                        connector.getKind(),
                        complianceRequestDto
                );
                logger.debug("Certificate Compliance Response from Connector: {}", responseDto);

                for (ComplianceResponseRulesDto rule : responseDto.getRules()) {
                    Long ruleId = getComplianceRuleEntity(rule.getUuid(),
                            connectorService.getConnectorEntity(connector.getConnectorUuid()), connector.getKind()).getId();
                    switch (rule.getStatus()) {
                        case OK:
                            complianceResults.getOk().add(ruleId);
                            break;
                        case NOK:
                            complianceResults.getNok().add(ruleId);
                            break;
                        case NA:
                            complianceResults.getNa().add(ruleId);
                    }
                }
                logger.debug("Status from the Connector: {}", responseDto.getStatus());
                allStatuses.add(responseDto.getStatus());
            }
        }
        ComplianceStatus overallStatus = computeOverallComplianceStatus(allStatuses);
        logger.debug("Overall Status: {}", overallStatus);
        setComplianceForCertificate(certificate.getUuid(), overallStatus, complianceResults);
    }

    @Override
    @Async
    public void complianceCheckForRaProfile(String uuid) throws ConnectorException {
        RaProfile raProfileDto = raProfileService.getRaProfileEntity(uuid);
        logger.debug("Checking compliance for all the certificates in RA Profile");
        complianceCheckForRaProfile(raProfileDto);
    }


    @Override
    @Async
    public void complianceCheckForComplianceProfile(String uuid) throws ConnectorException {
        ComplianceProfile complianceProfile = complianceProfileService.getComplianceProfileEntity(uuid);
        logger.debug("Checking the compliance for all the Certificates with profile: {}", complianceProfile);
        Set<RaProfile> raProfiles = complianceProfile.getRaProfiles();
        for (RaProfile raProfile : raProfiles) {
            complianceCheckForRaProfile(raProfile);
        }
    }

    @Override
    public ComplianceRule getComplianceRuleEntity(Long id) {
        return complianceRuleRepository.getById(id);
    }

    @Override
    public List<ComplianceRule> getComplianceRuleEntityForIds(List<Long> ids) {
        return complianceRuleRepository.findByIdIn(ids);
    }


    private void complianceCheckForRaProfile(RaProfile raProfile) throws ConnectorException {
        List<Certificate> certificates = certificateService.listCertificatesForRaProfile(raProfile);
        for (Certificate certificate : certificates) {
            checkComplianceOfCertificate(certificate);
        }
    }

    private void setComplianceForCertificate(String uuid, ComplianceStatus status,
                                             CertificateComplianceStorageDto result) throws NotFoundException {
        certificateService.updateComplianceReport(uuid, status, result);
    }

    private List<ComplianceRequestRulesDto> getComplianceRequestRules(List<ComplianceRulesDto> rules) {
        List<ComplianceRequestRulesDto> dtos = new ArrayList<>();
        List<String> nonDuplicateUuids = new ArrayList<>();
        for (ComplianceRulesDto rule : rules) {
            if(nonDuplicateUuids.contains(rule.getUuid())){
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

    private ComplianceStatus computeOverallComplianceStatus(List<ComplianceStatus> statuses) {
        if (statuses.contains(ComplianceStatus.NOK)) {
            return ComplianceStatus.NOK;
        }
        if (statuses.stream().allMatch(s -> s.equals(ComplianceStatus.NA))) {
            return ComplianceStatus.NA;
        }
        if (statuses.stream().allMatch(s -> List.of(ComplianceStatus.OK, ComplianceStatus.NA).contains(s))) {
            return ComplianceStatus.OK;
        }
        return ComplianceStatus.NA;
    }
}
