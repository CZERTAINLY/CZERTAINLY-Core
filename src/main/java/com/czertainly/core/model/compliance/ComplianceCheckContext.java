package com.czertainly.core.model.compliance;

import com.czertainly.api.clients.v2.ComplianceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.connector.compliance.v2.ComplianceResponseDto;
import com.czertainly.api.model.connector.compliance.v2.ComplianceResponseRuleDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.ComplianceRuleStatus;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.dao.entity.ComplianceSubject;
import com.czertainly.core.service.handler.ComplianceProfileRuleHandler;
import com.czertainly.core.service.handler.ComplianceSubjectHandler;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Getter
public class ComplianceCheckContext {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceCheckContext.class);

    private final Resource resource;
    private final IPlatformEnum typeEnum;
    private final boolean checkByProfiles;

    private final ComplianceProfileRuleHandler ruleHandler;
    private final Map<Resource, ComplianceSubjectHandler<? extends ComplianceSubject>> subjectHandlers;

    private final ComplianceApiClient complianceApiClient;
    private final com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1;

    private final Map<UUID, ComplianceCheckProfileContext> profilesContextMap = new HashMap<>();
    private final Map<String, ComplianceCheckProviderContext> providersContextMap = new HashMap<>();

    public ComplianceCheckContext(boolean checkByProfiles, Resource resource, IPlatformEnum typeEnum, ComplianceProfileRuleHandler ruleHandler, Map<Resource, ComplianceSubjectHandler<? extends ComplianceSubject>> subjectHandlers, ComplianceApiClient complianceApiClient, com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1) {
        this.checkByProfiles = checkByProfiles;
        this.resource = resource;
        this.typeEnum = typeEnum;
        this.ruleHandler = ruleHandler;
        this.subjectHandlers = subjectHandlers;
        this.complianceApiClient = complianceApiClient;
        this.complianceApiClientV1 = complianceApiClientV1;
    }

    public void addComplianceProfile(ComplianceProfile complianceProfile, Map<Resource, Set<ComplianceSubject>> complianceSubjects) {
        if (complianceProfile.getComplianceRules().isEmpty() || complianceSubjects == null || complianceSubjects.isEmpty()) {
            return;
        }

        ComplianceCheckProfileContext profileContext = profilesContextMap.computeIfAbsent(complianceProfile.getUuid(), uuid -> new ComplianceCheckProfileContext(complianceSubjects));
        for (ComplianceProfileRule profileRule : complianceProfile.getComplianceRules()) {
            // skip rules that do not match the resource and type
            if (skipProfileRule(profileRule, null)) {
                continue;
            }

            String providerKey = profileRule.getInternalRuleUuid() != null ? null : "%s|%s".formatted(profileRule.getConnectorUuid(), profileRule.getKind());
            profileContext.addProfileRule(providerKey, profileRule);
            // load compliance providers context
            if (providerKey != null) {
                ComplianceCheckProviderContext providerContext = providersContextMap.computeIfAbsent(providerKey, k -> new ComplianceCheckProviderContext(profileRule.getConnector(), profileRule.getKind(), ruleHandler, complianceApiClient, complianceApiClientV1));
                if (profileRule.getComplianceRuleUuid() != null) {
                    providerContext.getRulesBatchRequestDto().getRuleUuids().add(profileRule.getComplianceRuleUuid());
                } else {
                    providerContext.getRulesBatchRequestDto().getGroupUuids().add(profileRule.getComplianceGroupUuid());
                }
            }
        }
    }

    public void performComplianceCheck() {
        logger.debug("Starting performComplianceCheck for {} profiles and {} providers", profilesContextMap.size(), providersContextMap.size());
        for (ComplianceCheckProfileContext profileContext : profilesContextMap.values()) {
            for (Map.Entry<Resource, Set<ComplianceSubject>> resourceObjects : profileContext.getComplianceSubjects().entrySet()) {
                ComplianceSubjectHandler<? extends ComplianceSubject> subjectHandler = subjectHandlers.get(resourceObjects.getKey());
                for (var subject : resourceObjects.getValue()) {
                    try {
                        subjectHandler.initSubjectComplianceResult(subject.getUuid(), subject.getComplianceResult());
                        performSubjectComplianceCheck(profileContext, subjectHandler, resourceObjects.getKey(), subject);
                        subjectHandler.saveComplianceResult(subject, null);
                        logger.debug("{} {} compliance check finalized with result: {}", resourceObjects.getKey().getLabel(), subject.getUuid(), subject.getComplianceStatus().getLabel());
                    } catch (Exception e) {
                        logger.warn("Error checking compliance for {} with UUID {}: {}", resourceObjects.getKey().getLabel(), subject.getUuid(), e.getMessage());
                        subjectHandler.saveComplianceResult(subject, e.getMessage());
                    }
                }
            }
        }
    }

    private void performSubjectComplianceCheck(ComplianceCheckProfileContext profileContext, ComplianceSubjectHandler<? extends ComplianceSubject> subjectHandler, Resource resource, ComplianceSubject subject) throws ConnectorException, NotFoundException {
        for (ComplianceProfileRule profileRule : profileContext.getInternalRules()) {
            // skip rules that do not match the resource and type
            if (skipProfileRule(profileRule, resource)) {
                continue;
            }
            subjectHandler.evaluateInternalRule(profileRule, subject);
        }

        for (var providerRules : profileContext.getProviderRulesMapping().entrySet()) {
            ComplianceCheckProviderContext providerContext = providersContextMap.get(providerRules.getKey());
            providerContext.prepareComplianceCheckRequest(subject, resource, subject.getType());
            for (ComplianceProfileRule profileRule : providerRules.getValue()) {
                // skip rules that do not match the resource and type
                if (skipProfileRule(profileRule, resource)
                        // skip if rule was already checked for compliance check of this subject
                        || subjectHandler.wasAlreadyChecked(subject.getUuid(), providerRules.getKey(), profileRule)) {
                    continue;
                }

                // add rule to compliance check request, if returns non-null status, it means the rule is not available or not applicable. In case of null, the rule will be checked by the provider.
                ComplianceRuleStatus ruleStatus = providerContext.addProfileRuleToCheck(profileRule);
                subjectHandler.addProviderRuleResult(subject.getUuid(), providerRules.getKey(), providerContext.getConnectorUuid(), providerContext.getKind(), profileRule.getComplianceRuleUuid(), profileRule.getComplianceGroupUuid(), ruleStatus);
            }
            ComplianceResponseDto complianceResponse = providerContext.executeComplianceCheck();
            for (ComplianceResponseRuleDto responseRule : complianceResponse.getRules()) {
                subjectHandler.addProviderRuleResult(subject.getUuid(), providerRules.getKey(), providerContext.getConnectorUuid(), providerContext.getKind(), responseRule.getUuid(), null, responseRule.getStatus());
            }
        }
    }

    private boolean skipProfileRule(ComplianceProfileRule profileRule, Resource subjectResource) {
        if (subjectResource != null && profileRule.getResource() != subjectResource) {
            return true;
        }
        return (resource != null && resource != profileRule.getResource()) || (typeEnum != null && !typeEnum.name().equals(profileRule.getType()));
    }

}
