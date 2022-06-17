package com.czertainly.core.service.impl;

import com.czertainly.api.clients.ComplianceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.compliance.ComplianceRequestDto;
import com.czertainly.api.model.connector.compliance.ComplianceRequestRulesDto;
import com.czertainly.api.model.connector.compliance.ComplianceResponseDto;
import com.czertainly.api.model.core.certificate.CertificateComplianceResultDto;
import com.czertainly.api.model.core.compliance.ComplianceConnectorAndRulesDto;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceRulesDto;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    @Override
    public void checkComplianceOfCertificate(Certificate certificate) throws ConnectorException {
        RaProfile raProfile = certificate.getRaProfile();
        List<CertificateComplianceResultDto> complianceResults = new ArrayList<>();
        List<ComplianceStatus> allStatuses = new ArrayList<>();
        if (raProfile == null) {
            logger.warn("Certificate with uuid: {} does not have any RA Profile association", certificate.getUuid());
            return;
        }
        ComplianceProfile complianceProfile = raProfile.getComplianceProfile();
        if (complianceProfile == null) {
            logger.warn("Certificate with uuid: {} does not have any Compliance Profile association", certificate.getUuid());
            return;
        }
        for (ComplianceConnectorAndRulesDto connector : complianceProfile.mapToDto().getRules()) {
            ComplianceRequestDto complianceRequestDto = new ComplianceRequestDto();
            complianceRequestDto.setCertificate(certificate.getCertificateContent().getContent());
            complianceRequestDto.setRules(getComplianceRequestRules(connector.getRules()));
            ComplianceResponseDto responseDto = complianceApiClient.checkCompliance(
                    connectorService.getConnector(connector.getConnectorUuid()),
                    connector.getKind(),
                    complianceRequestDto
            );
            complianceResults.add(formatComplianceResult(responseDto,
                    connectorService.getConnector(connector.getConnectorUuid()),
                    connector.getKind()));
            allStatuses.add(responseDto.getStatus());
        }
        ComplianceStatus overallStatus = computeOverallComplianceStatus(allStatuses);
        setComplianceForCertificate(certificate.getUuid(), overallStatus, complianceResults);
    }

    @Override
    public void complianceCheckForRaProfile(String uuid) throws NotFoundException, ConnectorException {
        RaProfile raProfileDto = raProfileService.getRaProfileEntity(uuid);
        complianceCheckForRaProfile(raProfileDto);
    }

    @Override
    public void complianceCheckForDiscovery(String uuid) throws NotFoundException {
        //TODO
    }

    @Override
    public void complianceCheckForComplianceProfile(String uuid) throws ConnectorException {
        ComplianceProfile complianceProfile = complianceProfileService.getComplianceProfileEntity(uuid);
        Set<RaProfile> raProfiles = complianceProfile.getRaProfiles();
        for(RaProfile raProfile: raProfiles){
            complianceCheckForRaProfile(raProfile);
        }
    }

    private void complianceCheckForRaProfile(RaProfile raProfile) throws ConnectorException {
        List<Certificate> certificates = certificateService.listCertificatesForRaProfile(raProfile);
        for(Certificate certificate: certificates){
            checkComplianceOfCertificate(certificate);
        }
    }

    private void setComplianceForCertificate(String uuid, ComplianceStatus status,
                                             List<CertificateComplianceResultDto> result) throws NotFoundException {
        certificateService.updateComplianceReport(uuid, status, result);
    }

    private List<ComplianceRequestRulesDto> getComplianceRequestRules(List<ComplianceRulesDto> rules) {
        List<ComplianceRequestRulesDto> dtos = new ArrayList<>();
        for (ComplianceRulesDto rule : rules) {
            ComplianceRequestRulesDto dto = new ComplianceRequestRulesDto();
            dto.setUuid(rule.getUuid());
            dto.setAttributes(rule.getAttributes());
            dtos.add(dto);
        }
        return dtos;
    }

    private ComplianceStatus computeOverallComplianceStatus(List<ComplianceStatus> statuses) {
        if (statuses.contains(ComplianceStatus.NON_COMPLIANT)) {
            return ComplianceStatus.NON_COMPLIANT;
        }
        if (statuses.stream().allMatch(s -> s.equals(ComplianceStatus.NOT_CHECKED) ||
                s.equals(ComplianceStatus.UNKNOWN))) {
            return ComplianceStatus.NOT_CHECKED;
        }
        if (statuses.stream().allMatch(s -> List.of(ComplianceStatus.COMPLIANT, ComplianceStatus.NOT_CHECKED,
                ComplianceStatus.UNKNOWN).contains(s))) {
            return ComplianceStatus.COMPLIANT;
        }
        return ComplianceStatus.UNKNOWN;
    }

    private CertificateComplianceResultDto formatComplianceResult(ComplianceResponseDto response,
                                                                  ConnectorDto connector, String kind) {
        CertificateComplianceResultDto complianceResultDto = new CertificateComplianceResultDto();
        complianceResultDto.setConnectorName(connector.getName());
        complianceResultDto.setConnectorUuid(connector.getUuid());
        complianceResultDto.setKind(kind);
        complianceResultDto.setStatus(response.getStatus());
        complianceResultDto.setRules(response.getRules());
        return complianceResultDto;
    }
}
