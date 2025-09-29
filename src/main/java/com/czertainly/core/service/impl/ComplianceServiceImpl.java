package com.czertainly.core.service.impl;

import com.czertainly.api.clients.v2.ComplianceApiClient;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.connector.compliance.v2.ComplianceRuleResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.ComplianceRuleStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckResultDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.evaluator.TriggerEvaluator;
import com.czertainly.core.model.compliance.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.handler.ComplianceProfileRuleHandler;
import com.czertainly.core.service.handler.ComplianceSubjectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ComplianceServiceImpl implements ComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceServiceImpl.class);

    //    private PlatformTransactionManager transactionManager;
    private ComplianceApiClient complianceApiClient;
    private com.czertainly.api.clients.ComplianceApiClient complianceApiClientV1;

    private ComplianceProfileRepository complianceProfileRepository;
    private ComplianceProfileAssociationRepository complianceProfileAssociationRepository;
    private ComplianceInternalRuleRepository internalRuleRepository;

    private CertificateRepository certificateRepository;
    private CertificateRequestRepository certificateRequestRepository;
    private CryptographicKeyRepository cryptographicKeyRepository;
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    // since only checking condition items, not necessary CertificateTriggerEvaluator that implements special logic for setting properties
    private TriggerEvaluator<Certificate> certificateTriggerEvaluator;
    private TriggerEvaluator<CertificateRequestEntity> certificateRequestTriggerEvaluator;
    private TriggerEvaluator<CryptographicKeyItem> cryptographicKeyItemTriggerEvaluator;

    private ComplianceProfileRuleHandler ruleHandler;

//    @Autowired
//    public void setTransactionManager(PlatformTransactionManager transactionManager) {
//        this.transactionManager = transactionManager;
//    }

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
    public void setComplianceProfileAssociationRepository(ComplianceProfileAssociationRepository complianceProfileAssociationRepository) {
        this.complianceProfileAssociationRepository = complianceProfileAssociationRepository;
    }

    @Autowired
    public void setInternalRuleRepository(ComplianceInternalRuleRepository internalRuleRepository) {
        this.internalRuleRepository = internalRuleRepository;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setCertificateRequestRepository(CertificateRequestRepository certificateRequestRepository) {
        this.certificateRequestRepository = certificateRequestRepository;
    }

    @Autowired
    public void setCryptographicKeyRepository(CryptographicKeyRepository cryptographicKeyRepository) {
        this.cryptographicKeyRepository = cryptographicKeyRepository;
    }

    @Autowired
    public void setCryptographicKeyItemRepository(CryptographicKeyItemRepository cryptographicKeyItemRepository) {
        this.cryptographicKeyItemRepository = cryptographicKeyItemRepository;
    }

    @Lazy
    @Autowired
    public void setCertificateTriggerEvaluator(TriggerEvaluator<Certificate> certificateTriggerEvaluator) {
        this.certificateTriggerEvaluator = certificateTriggerEvaluator;
    }

    @Autowired
    public void setCertificateRequestTriggerEvaluator(TriggerEvaluator<CertificateRequestEntity> certificateRequestTriggerEvaluator) {
        this.certificateRequestTriggerEvaluator = certificateRequestTriggerEvaluator;
    }

    @Autowired
    public void setCryptographicKeyItemTriggerEvaluator(TriggerEvaluator<CryptographicKeyItem> cryptographicKeyItemTriggerEvaluator) {
        this.cryptographicKeyItemTriggerEvaluator = cryptographicKeyItemTriggerEvaluator;
    }

    @Autowired
    public void setRuleHandler(ComplianceProfileRuleHandler ruleHandler) {
        this.ruleHandler = ruleHandler;
    }

    @Override
    public ComplianceCheckResultDto getComplianceCheckResult(Resource resource, UUID objectUuid) throws NotFoundException {
        if (!resource.complianceSubject()) {
            throw new ValidationException("Cannot get compliance result for resource %s. Resource does not support compliance check".formatted(resource.getLabel()));
        }

        ComplianceSubject complianceSubject = switch (resource) {
            case CERTIFICATE ->
                    complianceSubject = certificateRepository.findByUuid(objectUuid).orElseThrow(() -> new NotFoundException(Certificate.class, objectUuid));
            case CERTIFICATE_REQUEST ->
                    complianceSubject = certificateRequestRepository.findByUuid(objectUuid).orElseThrow(() -> new NotFoundException(CertificateRequestEntity.class, objectUuid));
            case CRYPTOGRAPHIC_KEY_ITEM ->
                    complianceSubject = cryptographicKeyItemRepository.findByUuid(objectUuid).orElseThrow(() -> new NotFoundException(CryptographicKeyItem.class, objectUuid));
            default ->
                    throw new ValidationException("Cannot get compliance result for resource %s. Resource does not support compliance check".formatted(resource.getLabel()));
        };

        return getComplianceCheckResult(resource, objectUuid, complianceSubject.getComplianceResult());
    }

    @Override
    public ComplianceCheckResultDto getComplianceCheckResult(Resource resource, UUID objectUuid, ComplianceResultDto complianceResult) {
        ComplianceCheckResultDto resultDto = new ComplianceCheckResultDto();
        if (complianceResult == null) {
            resultDto.setStatus(ComplianceStatus.NOT_CHECKED);
            return resultDto;
        }

        resultDto.setStatus(complianceResult.getStatus());
        resultDto.setTimestamp(complianceResult.getTimestamp());
        // load internal rules
        if (complianceResult.getInternalRules() != null) {
            List<UUID> failedInternalRulesUuids = new ArrayList<>(complianceResult.getInternalRules().getNotCompliant());
            failedInternalRulesUuids.addAll(complianceResult.getInternalRules().getNotApplicable());
            failedInternalRulesUuids.addAll(complianceResult.getInternalRules().getNotAvailable());
            internalRuleRepository.findByUuidIn(failedInternalRulesUuids).forEach(internalRule -> {
                ComplianceRuleStatus status = popFailedRuleWithStatus(complianceResult.getInternalRules(), internalRule.getUuid());
                resultDto.getFailedRules().add(internalRule.mapToComplianceCheckRuleDto(status));
            });

            // should we handle internal rules that are not existing anymore?
        }

        // load provider rules
        for (ComplianceResultProviderRulesDto providerRules : complianceResult.getProviderRules()) {
            Set<UUID> failedProviderRulesUuids = new HashSet<>(providerRules.getNotCompliant());
            failedProviderRulesUuids.addAll(providerRules.getNotApplicable());
            failedProviderRulesUuids.addAll(providerRules.getNotAvailable());
            ComplianceRulesGroupsBatchDto batchDto = null;
            try {
                batchDto = ruleHandler.getComplianceProviderRulesBatch(providerRules.getConnectorUuid(), providerRules.getKind(), failedProviderRulesUuids, null, false);
            } catch (Exception e) {
                logger.warn("Cannot retrieve compliance rules from provider {} of kind {} when getting compliance result rules of {} with UUID {}. Marking all {} rules as Not Available", providerRules.getConnectorUuid(), providerRules.getKind(), resource, objectUuid, failedProviderRulesUuids.size());
                batchDto = new ComplianceRulesGroupsBatchDto();
                batchDto.setConnectorUuid(providerRules.getConnectorUuid());
                batchDto.setKind(providerRules.getKind());
            }

            for (UUID failedRuleUuid : failedProviderRulesUuids) {
                ComplianceRuleStatus status = popFailedRuleWithStatus(providerRules, failedRuleUuid);
                ComplianceRuleResponseDto providerRule = batchDto.getRules().get(failedRuleUuid);
                resultDto.getFailedRules().add(ruleHandler.mapComplianceCheckProviderRuleDto(batchDto.getConnectorUuid(), batchDto.getConnectorName(), batchDto.getKind(), failedRuleUuid, status, providerRule));
            }
        }

        return resultDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkComplianceValidation(List<SecuredUUID> uuids, Resource resource, String type) throws ValidationException, NotFoundException {
        if (resource != null) {
            if (!resource.complianceSubject()) {
                throw new ValidationException("Cannot check compliance for resource %s. Resource does not support compliance check".formatted(resource.getLabel()));
            }
            ComplianceProfileRuleHandler.getComplianceRuleTypeFromCode(resource, type);
        } else if (type != null) {
            throw new ValidationException("Cannot check compliance for type %s. Resource must be specified when type is specified".formatted(type));
        }

        for (SecuredUUID uuid : uuids) {
            if (!complianceProfileRepository.existsById(uuid.getValue())) {
                throw new NotFoundException("Cannot check compliance. Compliance profile with UUID %s not found".formatted(uuid.getValue()));
            }
        }
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkComplianceAsync(List<SecuredUUID> uuids, Resource resource, String type) {
        checkCompliance(uuids, resource, type);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkCompliance(List<SecuredUUID> uuids, Resource resource, String type) {
        IPlatformEnum typeEnum = null;
        if (resource != null) {
            if (!resource.complianceSubject()) {
                throw new ValidationException("Cannot check compliance for resource %s. Resource does not support compliance check".formatted(resource.getLabel()));
            }
            typeEnum = ComplianceProfileRuleHandler.getComplianceRuleTypeFromCode(resource, type);
        } else if (type != null) {
            throw new ValidationException("Cannot check compliance for type %s. Resource must be specified when type is specified".formatted(type));
        }

        // load compliance profiles
        ComplianceCheckContext context = new ComplianceCheckContext(true, resource, typeEnum, ruleHandler, getSubjectHandlers(true), complianceApiClient, complianceApiClientV1);
        List<ComplianceProfile> complianceProfiles = complianceProfileRepository.findWithAssociationsByUuidIn(uuids.stream().map(SecuredUUID::getValue).toList()).stream()
                .filter(p -> !p.getAssociations().isEmpty() && !p.getComplianceRules().isEmpty()).toList();
        for (ComplianceProfile profile : complianceProfiles) {
            // load compliance subjects
            Map<Resource, Set<ComplianceSubject>> complianceSubjects = new EnumMap<>(Resource.class);
            for (ComplianceProfileAssociation association : profile.getAssociations()) {
                if (!isAssociationResourceCompatible(association.getResource(), resource)) {
                    continue;
                }

                List<ComplianceSubject> subjects = getComplianceSubjects(association.getResource(), association.getObjectUuid(), typeEnum);
                if (!subjects.isEmpty()) {
                    complianceSubjects.computeIfAbsent(getSubjectResouceByAssociationResource(association.getResource()), r -> new HashSet<>()).addAll(subjects);
                }
            }

            // if no subjects found for any association, skip the profile
            if (complianceSubjects.isEmpty()) {
                continue;
            }
            context.addComplianceProfile(profile, complianceSubjects);
        }
        context.performComplianceCheck();
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectsComplianceValidation(Resource resource, List<UUID> objectUuids) throws ValidationException, NotFoundException {
        if (resource == Resource.CERTIFICATE_REQUEST || (!resource.complianceSubject() && !resource.hasComplianceProfiles())) {
            throw new ValidationException("Cannot check compliance for resource %s. Resource does not support compliance check or does not allow association of compliance profiles".formatted(resource.getLabel()));
        }

        for (UUID objectUuid : objectUuids) {
            boolean exists = switch (resource) {
                case CERTIFICATE -> certificateRepository.existsById(objectUuid);
                case CRYPTOGRAPHIC_KEY -> cryptographicKeyRepository.existsById(objectUuid);
                case CRYPTOGRAPHIC_KEY_ITEM -> cryptographicKeyItemRepository.existsById(objectUuid);
                case RA_PROFILE ->
                        complianceProfileAssociationRepository.countByResourceAndObjectUuid(Resource.RA_PROFILE, objectUuid) > 0;
                case TOKEN_PROFILE ->
                        complianceProfileAssociationRepository.countByResourceAndObjectUuid(Resource.TOKEN_PROFILE, objectUuid) > 0;
                default ->
                        throw new ValidationException("Cannot check compliance for resource %s. Resource does not support compliance check".formatted(resource.getLabel()));
            };
            if (!exists) {
                throw new NotFoundException("Cannot check compliance. %s with UUID %s not found".formatted(resource.getLabel(), objectUuid));
            }
        }
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectComplianceAsync(Resource resource, UUID objectUuid) {
        checkResourceObjectsComplianceAsync(resource, List.of(objectUuid));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectCompliance(Resource resource, UUID objectUuid) {
        checkResourceObjectsComplianceAsync(resource, List.of(objectUuid));
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.CHECK_COMPLIANCE)
    public void checkResourceObjectsComplianceAsync(Resource resource, List<UUID> objectUuids) {
        if (resource == Resource.CERTIFICATE_REQUEST || (!resource.complianceSubject() && !resource.hasComplianceProfiles())) {
            throw new ValidationException("Cannot check compliance for resource %s. Resource does not support compliance check or does not allow association of compliance profiles".formatted(resource.getLabel()));
        }

        Map<UUID, ComplianceProfile> complianceProfilesMap = new HashMap<>();
        Map<UUID, Map<Resource, Set<ComplianceSubject>>> complianceProfileSubjectsMap = new HashMap<>();
        switch (resource) {
            case CERTIFICATE -> {
                List<Certificate> certificates = certificateRepository.findByUuidInAndArchivedFalse(objectUuids);
                for (Certificate certificate : certificates) {
                    ComplianceSubject subject = certificate.getCertificateContentId() != null ? certificate : certificate.getCertificateRequestEntity();
                    if (certificate.getRaProfileUuid() == null || subject == null) continue;

                    loadComplianceProfilesWithSubjects(complianceProfilesMap, complianceProfileSubjectsMap, Resource.RA_PROFILE, certificate.getRaProfileUuid(), certificate.getCertificateContentId() != null ? Resource.CERTIFICATE : Resource.CERTIFICATE_REQUEST, List.of(subject));
                }
            }
            case CRYPTOGRAPHIC_KEY -> {
                List<CryptographicKey> keys = cryptographicKeyRepository.findByUuidIn(objectUuids);
                for (CryptographicKey key : keys) {
                    if (key.getTokenProfileUuid() == null) continue;
                    loadComplianceProfilesWithSubjects(complianceProfilesMap, complianceProfileSubjectsMap, Resource.TOKEN_PROFILE, key.getTokenProfileUuid(), Resource.CRYPTOGRAPHIC_KEY, new ArrayList<>(key.getItems()));
                }
            }
            case CRYPTOGRAPHIC_KEY_ITEM -> {
                List<CryptographicKeyItem> keyItems = cryptographicKeyItemRepository.findByUuidIn(objectUuids);
                for (CryptographicKeyItem keyItem : keyItems) {
                    if (keyItem.getKey() == null || keyItem.getKey().getTokenProfileUuid() == null) continue;
                    loadComplianceProfilesWithSubjects(complianceProfilesMap, complianceProfileSubjectsMap, Resource.TOKEN_PROFILE, keyItem.getKey().getTokenProfileUuid(), Resource.CRYPTOGRAPHIC_KEY, List.of(keyItem));
                }
            }
            case RA_PROFILE -> {
                for (UUID associationObjectUuid : objectUuids) {
                    if (complianceProfileAssociationRepository.countByResourceAndObjectUuid(Resource.RA_PROFILE, associationObjectUuid) == 0) {
                        continue;
                    }
                    List<Certificate> certificates = certificateRepository.findByRaProfileUuidAndCertificateContentIdNotNullAndArchivedFalse(associationObjectUuid);
                    loadComplianceProfilesWithSubjects(complianceProfilesMap, complianceProfileSubjectsMap, Resource.RA_PROFILE, associationObjectUuid, Resource.CERTIFICATE, new ArrayList<>(certificates));
                }
            }
            case TOKEN_PROFILE -> {
                for (UUID associationObjectUuid : objectUuids) {
                    if (complianceProfileAssociationRepository.countByResourceAndObjectUuid(Resource.TOKEN_PROFILE, associationObjectUuid) == 0) {
                        continue;
                    }
                    List<CryptographicKeyItem> keyItems = cryptographicKeyItemRepository.findByKeyTokenProfileUuid(associationObjectUuid);
                    loadComplianceProfilesWithSubjects(complianceProfilesMap, complianceProfileSubjectsMap, Resource.TOKEN_PROFILE, associationObjectUuid, Resource.CRYPTOGRAPHIC_KEY, new ArrayList<>(keyItems));
                }
            }
            default ->
                    throw new ValidationException("Cannot check compliance for resource %s. Resource does not support compliance check".formatted(resource.getLabel()));
        }

        ComplianceCheckContext context = new ComplianceCheckContext(false, null, null, ruleHandler, getSubjectHandlers(false), complianceApiClient, complianceApiClientV1);
        for (ComplianceProfile profile : complianceProfilesMap.values()) {
            context.addComplianceProfile(profile, complianceProfileSubjectsMap.get(profile.getUuid()));
        }
        context.performComplianceCheck();
    }

    private ComplianceRuleStatus popFailedRuleWithStatus(ComplianceResultRulesDto complianceResultRules, UUID ruleUuid) {
        if (complianceResultRules.getNotCompliant().remove(ruleUuid)) {
            return ComplianceRuleStatus.NOK;
        } else if (complianceResultRules.getNotApplicable().remove(ruleUuid)) {
            return ComplianceRuleStatus.NA;
        } else if (complianceResultRules.getNotAvailable().remove(ruleUuid)) {
            return ComplianceRuleStatus.NOT_AVAILABLE;
        }
        return ComplianceRuleStatus.NOT_AVAILABLE;
    }

    private void loadComplianceProfilesWithSubjects(Map<UUID, ComplianceProfile> complianceProfilesMap, Map<UUID, Map<Resource, Set<ComplianceSubject>>> complianceProfileSubjectsMap, Resource resource, UUID associationObjectUuid, Resource subjectResource, Collection<ComplianceSubject> complianceSubjects) {
        List<ComplianceProfile> complianceProfiles = complianceProfileAssociationRepository.findDistinctByResourceAndObjectUuid(resource, associationObjectUuid).stream().map(ComplianceProfileAssociation::getComplianceProfile).toList();
        for (ComplianceProfile profile : complianceProfiles) {
            complianceProfilesMap.putIfAbsent(profile.getUuid(), profile);
            complianceProfileSubjectsMap.computeIfAbsent(profile.getUuid(), k -> new EnumMap<>(Resource.class))
                    .computeIfAbsent(subjectResource, r -> new HashSet<>())
                    .addAll(complianceSubjects);
        }
    }

    private boolean isAssociationResourceCompatible(Resource associationResource, Resource resource) {
        if (resource == null) {
            return true;
        }

        return switch (resource) {
            case CERTIFICATE, CERTIFICATE_REQUEST ->
                    associationResource == Resource.CERTIFICATE || associationResource == Resource.CERTIFICATE_REQUEST || associationResource == Resource.RA_PROFILE;
            case CRYPTOGRAPHIC_KEY, CRYPTOGRAPHIC_KEY_ITEM ->
                    associationResource == Resource.CRYPTOGRAPHIC_KEY || associationResource == Resource.CRYPTOGRAPHIC_KEY_ITEM || associationResource == Resource.TOKEN_PROFILE;
            default -> false;
        };
    }

    private Resource getSubjectResouceByAssociationResource(Resource associationResource) {
        return switch (associationResource) {
            case CERTIFICATE, RA_PROFILE -> Resource.CERTIFICATE;
            case CERTIFICATE_REQUEST -> Resource.CERTIFICATE_REQUEST;
            case CRYPTOGRAPHIC_KEY, CRYPTOGRAPHIC_KEY_ITEM, TOKEN_PROFILE -> Resource.CRYPTOGRAPHIC_KEY;
            default ->
                    throw new ValidationException("Cannot determine compliance subject resource for association resource %s".formatted(associationResource.getLabel()));
        };
    }

    private <T extends ComplianceSubject> List<T> getComplianceSubjects(Resource associationResource, UUID associationObjectUuid, IPlatformEnum type) {
        // TODO: filter by access control or if user can check compliance, allow listing of objects as subjects for compliance check
        return (List<T>) switch (associationResource) {
            case CERTIFICATE ->
                    certificateRepository.findByUuidInAndCertificateContentIdNotNullAndArchivedFalse(List.of(associationObjectUuid));
            case CERTIFICATE_REQUEST ->
                    certificateRequestRepository.findByUuidIn(List.of(associationObjectUuid)).map(List::of).orElse(List.of());
            case CRYPTOGRAPHIC_KEY -> cryptographicKeyItemRepository.findByKeyUuidIn(List.of(associationObjectUuid));
            case CRYPTOGRAPHIC_KEY_ITEM ->
                    cryptographicKeyItemRepository.findByUuid(associationObjectUuid).map(List::of).orElse(List.of());
            case RA_PROFILE ->
                    certificateRepository.findByRaProfileUuidAndCertificateContentIdNotNullAndArchivedFalse(associationObjectUuid);
            case TOKEN_PROFILE -> cryptographicKeyItemRepository.findByKeyTokenProfileUuid(associationObjectUuid);
            default -> List.of();
        };
    }

    private Map<Resource, ComplianceSubjectHandler<? extends ComplianceSubject>> getSubjectHandlers(boolean checkByProfiles) {
        Map<Resource, ComplianceSubjectHandler<? extends ComplianceSubject>> map = new EnumMap<>(Resource.class);
        map.put(Resource.CERTIFICATE, new ComplianceSubjectHandler<>(checkByProfiles, certificateTriggerEvaluator, certificateRepository));
        map.put(Resource.CERTIFICATE_REQUEST, new ComplianceSubjectHandler<>(checkByProfiles, certificateRequestTriggerEvaluator, certificateRequestRepository));
        map.put(Resource.CRYPTOGRAPHIC_KEY, new ComplianceSubjectHandler<>(checkByProfiles, cryptographicKeyItemTriggerEvaluator, cryptographicKeyItemRepository));

        return map;
    }
}
