package com.czertainly.core.service.handler;

import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.compliance.ComplianceRuleStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.core.dao.entity.ComplianceProfileRule;
import com.czertainly.core.dao.entity.ComplianceSubject;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import com.czertainly.core.evaluator.TriggerEvaluator;
import com.czertainly.core.model.compliance.ComplianceResultDto;
import com.czertainly.core.model.compliance.ComplianceResultProviderRulesDto;
import com.czertainly.core.model.compliance.ComplianceResultRulesDto;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;

@Getter
public class ComplianceSubjectHandler<T extends ComplianceSubject> {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceSubjectHandler.class);

    // If compliance is checked by profiles, the compliance result fro subject is being updated and not built from scratch
    // This means only rules of chosen compliance profiles that are reevaluated will be partially updated and new compliance status will be calculated
    private final boolean checkByProfiles;
    private final TriggerEvaluator<T> triggerEvaluator;
    private final SecurityFilterRepository<T, UUID> repository;

    private final Map<UUID, Set<UUID>> checkedInternalRulesMap = new HashMap<>();
    private final Map<UUID, Map<String, Set<UUID>>> checkedProviderRulesMap = new HashMap<>();
    private final Map<UUID, Map<String, Set<UUID>>> checkedProviderGroupsMap = new HashMap<>();
    private final Map<UUID, ComplianceResultDto> complianceResultsMap = new HashMap<>();

    public ComplianceSubjectHandler(boolean checkByProfiles, TriggerEvaluator<T> triggerEvaluator, SecurityFilterRepository<T, UUID> repository) {
        this.checkByProfiles = checkByProfiles;
        this.triggerEvaluator = triggerEvaluator;
        this.repository = repository;
    }

    public void initSubjectComplianceResult(UUID subjectUuid, ComplianceResultDto existingResult) {
        if (checkByProfiles && existingResult != null) {
            complianceResultsMap.put(subjectUuid, existingResult);
        }
    }

    /**
     * Evaluate internal rule for the given subject. If the rule was already checked for the subject, it will be skipped.
     *
     * @param profileRule the profile rule containing the internal rule to be evaluated
     * @param subject     the subject to be evaluated
     */
    public void evaluateInternalRule(ComplianceProfileRule profileRule, ComplianceSubject subject) throws RuleException {
        T typedSubject = (T) subject;
        if (wasAlreadyChecked(subject.getUuid(), null, profileRule)) {
            return;
        }

        UUID internalRuleUuid = profileRule.getInternalRuleUuid();
        ComplianceResultDto complianceResultDto = complianceResultsMap.computeIfAbsent(subject.getUuid(), k -> new ComplianceResultDto());
        if (checkByProfiles && complianceResultDto.getInternalRules() != null) {
            // remove if compliance rule result exists to avoid duplicates and be rewritten by current result
            complianceResultDto.getInternalRules().getNotCompliant().remove(internalRuleUuid);
            complianceResultDto.getInternalRules().getNotApplicable().remove(internalRuleUuid);
            complianceResultDto.getInternalRules().getNotAvailable().remove(internalRuleUuid);
        }

        if (profileRule.getResource() != profileRule.getInternalRule().getResource()) {
            if (complianceResultDto.getInternalRules() == null) {
                complianceResultDto.setInternalRules(new ComplianceResultRulesDto());
            }
            complianceResultDto.getInternalRules().getNotApplicable().add(internalRuleUuid);
        }

        boolean valid;
        try {
            valid = triggerEvaluator.evaluateInternalRule(profileRule.getInternalRule(), typedSubject);
        } catch (RuleException e) {
            String message = "Failed evaluating internal rule %s: %s".formatted(profileRule.getInternalRule().getName(), e.getMessage());
            logger.warn(message);
            throw new RuleException(message);
        }
        if (!valid) {
            if (complianceResultDto.getInternalRules() == null) {
                complianceResultDto.setInternalRules(new ComplianceResultRulesDto());
            }
            complianceResultDto.getInternalRules().getNotCompliant().add(internalRuleUuid);
        }
        checkedInternalRulesMap.get(subject.getUuid()).add(internalRuleUuid);
    }

    /**
     * Checks whether a given profile rule was already evaluated for the specified subject.
     *
     * @param subjectUuid UUID of the subject being evaluated
     * @param providerKey provider identifier (may be null for internal rules)
     * @param profileRule the profile rule to check
     * @return true if the rule (internal, provider rule or provider group) was already checked for the subject
     */
    public boolean wasAlreadyChecked(UUID subjectUuid, String providerKey, ComplianceProfileRule profileRule) {
        UUID checkedUuid;
        Set<UUID> checkedSet;
        if (profileRule.getInternalRuleUuid() != null) {
            checkedUuid = profileRule.getInternalRuleUuid();
            checkedSet = checkedInternalRulesMap.computeIfAbsent(subjectUuid, k -> new java.util.HashSet<>());
        } else if (profileRule.getComplianceRuleUuid() != null) {
            checkedUuid = profileRule.getComplianceRuleUuid();
            checkedSet = checkedProviderRulesMap.computeIfAbsent(subjectUuid, k -> new java.util.HashMap<>()).computeIfAbsent(providerKey, k -> new java.util.HashSet<>());
        } else {
            checkedUuid = profileRule.getComplianceGroupUuid();
            checkedSet = checkedProviderGroupsMap.computeIfAbsent(subjectUuid, k -> new java.util.HashMap<>()).computeIfAbsent(providerKey, k -> new java.util.HashSet<>());
        }

        return checkedSet.contains(checkedUuid);
    }

    /**
     * Records the result of a provider rule evaluation for a subject and updates the in-memory compliance result.
     * If {@code groupUuid} is provided the group is just marked as checked
     *
     * @param subjectUuid   UUID of the subject the result belongs to
     * @param providerKey   key of the provider
     * @param connectorUuid UUID identifying the provider connector
     * @param kind          kind identifier
     * @param ruleUUid      UUID of the evaluated rule (may be null when groupUuid is provided)
     * @param groupUuid     UUID of the evaluated rule group (may be null)
     * @param ruleStatus    status returned by the provider for the evaluated rule
     */
    public void addProviderRuleResult(UUID subjectUuid, String providerKey, UUID connectorUuid, String kind, UUID ruleUUid, UUID groupUuid, ComplianceRuleStatus ruleStatus) {
        if (groupUuid != null) {
            checkedProviderGroupsMap.computeIfAbsent(subjectUuid, k -> new java.util.HashMap<>()).computeIfAbsent(providerKey, k -> new java.util.HashSet<>()).add(groupUuid);
            return;
        }

        checkedProviderRulesMap.computeIfAbsent(subjectUuid, k -> new java.util.HashMap<>()).computeIfAbsent(providerKey, k -> new java.util.HashSet<>()).add(ruleUUid);
        if (ruleStatus == null || (ruleStatus == ComplianceRuleStatus.OK && !checkByProfiles)) {
            // rule will be checked by the provider, do not update compliance result
            return;
        }

        // update compliance result
        ComplianceResultDto complianceResultDto = complianceResultsMap.computeIfAbsent(subjectUuid, k -> new ComplianceResultDto());
        if (complianceResultDto.getProviderRules() == null) {
            complianceResultDto.setProviderRules(new ArrayList<>());
        }
        ComplianceResultProviderRulesDto providerResult = complianceResultDto.getProviderRules().stream().filter(provider -> provider.getConnectorUuid().equals(connectorUuid) && provider.getKind().equals(kind)).findFirst().orElse(null);
        if (providerResult == null) {
            providerResult = new ComplianceResultProviderRulesDto();
            providerResult.setConnectorUuid(connectorUuid);
            providerResult.setKind(kind);
            complianceResultDto.getProviderRules().add(providerResult);
        } else if (checkByProfiles) {
            // remove if compliance rule result exists to avoid duplicates and be rewritten by current result
            providerResult.getNotCompliant().remove(ruleUUid);
            providerResult.getNotApplicable().remove(ruleUUid);
            providerResult.getNotAvailable().remove(ruleUUid);
        }

        switch (ruleStatus) {
            case ComplianceRuleStatus.NOK -> providerResult.getNotCompliant().add(ruleUUid);
            case ComplianceRuleStatus.NA -> providerResult.getNotApplicable().add(ruleUUid);
            default -> providerResult.getNotAvailable().add(ruleUUid);
        }
    }

    /**
     * Persists the calculated compliance result for the provided subject.
     * The method sets the result timestamp, computes the overall compliance status,
     * stores the result on the subject entity and saves it via the repository.
     *
     * @param subject the subject whose compliance result should be saved
     */
    public void saveComplianceResult(ComplianceSubject subject, String errorMessage) {
        T typedSubject = (T) subject;
        ComplianceResultDto complianceResultDto = complianceResultsMap.computeIfAbsent(subject.getUuid(), k -> new ComplianceResultDto());
        complianceResultDto.setTimestamp(OffsetDateTime.now());
        if (errorMessage != null) {
            complianceResultDto.setMessage(errorMessage);
            complianceResultDto.setStatus(ComplianceStatus.FAILED);
        } else {
            complianceResultDto.setStatus(calculateComplianceStatus(complianceResultDto));
        }

        typedSubject.setComplianceResult(complianceResultDto);
        typedSubject.setComplianceStatus(complianceResultDto.getStatus());
        repository.save(typedSubject);
    }

    private ComplianceStatus calculateComplianceStatus(ComplianceResultDto resultDto) {
        ComplianceStatus status = ComplianceStatus.OK;
        if (resultDto.getInternalRules() != null) {
            status = calculateComplianceStatus(resultDto.getInternalRules());
        }
        if (status == ComplianceStatus.NOK) {
            return ComplianceStatus.NOK;
        }
        if (resultDto.getProviderRules() != null) {
            for (ComplianceResultProviderRulesDto providerRules : resultDto.getProviderRules()) {
                ComplianceStatus providerStatus = calculateComplianceStatus(providerRules);
                if (providerStatus == ComplianceStatus.NOK) {
                    return ComplianceStatus.NOK;
                }
                if (providerStatus == ComplianceStatus.NA) {
                    status = ComplianceStatus.NA;
                }
            }
        }
        return status;
    }

    private ComplianceStatus calculateComplianceStatus(ComplianceResultRulesDto resultRulesDto) {
        if (!resultRulesDto.getNotCompliant().isEmpty() || !resultRulesDto.getNotAvailable().isEmpty()) {
            return ComplianceStatus.NOK;
        } else if (!resultRulesDto.getNotApplicable().isEmpty()) {
            return ComplianceStatus.NA;
        }
        return ComplianceStatus.OK;
    }
}
