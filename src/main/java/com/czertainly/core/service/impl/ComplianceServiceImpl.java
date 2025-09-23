package com.czertainly.core.service.impl;

import com.czertainly.api.clients.v2.ComplianceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckResultDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.ComplianceProfileAssociationRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRepository;
import com.czertainly.core.dao.repository.ComplianceProfileRuleRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.model.compliance.ComplianceCheckContext;
import com.czertainly.core.model.compliance.ComplianceCheckProviderContext;
import com.czertainly.core.model.compliance.ComplianceResultDto;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.handler.ComplianceProfileRuleHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ComplianceServiceImpl implements ComplianceService {

    private ComplianceApiClient complianceApiClient;
    private com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1;

    private ComplianceProfileRepository complianceProfileRepository;
    private ComplianceProfileRuleRepository complianceProfileRuleRepository;
    private ComplianceProfileAssociationRepository complianceProfileAssociationRepository;
    private ConnectorRepository connectorRepository;

    private ComplianceProfileRuleHandler ruleHandler;

    @Autowired
    public void setComplianceApiClient(ComplianceApiClient complianceApiClient) {
        this.complianceApiClient = complianceApiClient;
    }

    @Autowired
    public void setComplianceApiClientV1(com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1) {
        this.complianceApiClientV1 = complianceApiClientV1;
    }

    @Autowired
    public void setComplianceProfileRepository(ComplianceProfileRepository complianceProfileRepository) {
        this.complianceProfileRepository = complianceProfileRepository;
    }

    @Autowired
    public void setComplianceProfileRuleRepository(ComplianceProfileRuleRepository complianceProfileRuleRepository) {
        this.complianceProfileRuleRepository = complianceProfileRuleRepository;
    }

    @Autowired
    public void setComplianceProfileAssociationRepository(ComplianceProfileAssociationRepository complianceProfileAssociationRepository) {
        this.complianceProfileAssociationRepository = complianceProfileAssociationRepository;
    }

    @Autowired
    public void setConnectorRepository(ConnectorRepository connectorRepository) {
        this.connectorRepository = connectorRepository;
    }

    @Autowired
    public void setRuleHandler(ComplianceProfileRuleHandler ruleHandler) {
        this.ruleHandler = ruleHandler;
    }

    @Override
    public ComplianceCheckResultDto getComplianceCheckResult(ComplianceResultDto complianceResult) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkCompliance(List<SecuredUUID> uuids, Resource resource, String type) throws ConnectorException, NotFoundException {
        // load compliance profiles
        List<ComplianceProfile> complianceProfiles = complianceProfileRepository.findWithAssociationsByUuidIn(uuids.stream().map(SecuredUUID::getValue).toList());
        ComplianceCheckContext context = loadComplianceCheckContext(complianceProfiles, resource, type);

    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectCompliance(Resource resource, UUID objectUuid) {
        // will be implemented in compliance check V2 rewrite
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectsCompliance(Resource resource, List<UUID> objectUuids) {
        // will be implemented in compliance check V2 rewrite
    }

    private ComplianceCheckContext loadComplianceCheckContext(List<ComplianceProfile> complianceProfiles, Resource resource, String type) throws ConnectorException, NotFoundException {
        ComplianceCheckContext context = new ComplianceCheckContext(resource, type);
        for (ComplianceProfile profile : complianceProfiles) {
            if (profile.getAssociations().isEmpty() || profile.getComplianceRules().isEmpty()) {
                continue;
            }
            context.addComplianceProfile(profile);
        }

        return context;
    }

    private ComplianceResultDto performComplianceCheck(ComplianceCheckContext context) throws NotFoundException {
        ComplianceResultDto result = new ComplianceResultDto();
        result.setTimestamp(OffsetDateTime.now());
        Map<String, ComplianceCheckProviderContext> providerContextMap = context.getProviderRulesMapping();
        for (ComplianceCheckProviderContext providerContext : providerContextMap.values()) {
            // load connector
            Connector connector = connectorRepository.findByUuid(providerContext.getConnectorUuid()).orElseThrow(() -> new NotFoundException(Connector.class, providerContext.getConnectorUuid()));
            ConnectorDto connectorDto = connector.mapToDto();
            FunctionGroupCode complianceFunctionGroup = ruleHandler.validateComplianceProvider(connectorDto, providerContext.getKind());

            // perform compliance check
            if (complianceFunctionGroup == FunctionGroupCode.COMPLIANCE_PROVIDER_V2) {
                // V2 Compliance API
                complianceApiClient.checkCompliance(connectorDto, providerContext.getKind(), providerContext.getRulesBatchRequestDto());
            } else {
                // V1 Compliance API
                complianceApiClientV1.checkCompliance(connectorDto, providerContext.getKind(), providerContext.getRulesBatchRequestDto());
            }
        }

        return result;
    }
}
