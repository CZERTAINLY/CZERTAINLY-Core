package com.czertainly.core.service.compliance;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.UploadCertificateRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.IntegerAttributeContent;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceRuleStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckResultDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckRuleDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.helpers.CertificateGeneratorHelper;
import com.czertainly.core.model.compliance.ComplianceResultDto;
import com.czertainly.core.model.compliance.ComplianceResultProviderRulesDto;
import com.czertainly.core.model.compliance.ComplianceResultRulesDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ComplianceService;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class ComplianceServiceTest extends BaseComplianceTest {

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private TokenProfileRepository tokenProfileRepository;

    @Autowired
    private TokenInstanceReferenceRepository tokenRepository;

    @Autowired
    CryptographicKeyRepository cryptographicKeyRepository;

    @Test
    void testCheckCompliance() throws Exception {
        // add a V1 provider rule association to the seeded compliance profile
        var v1RuleAssoc = new ComplianceProfileRule();
        v1RuleAssoc.setComplianceProfile(complianceProfile);
        v1RuleAssoc.setComplianceProfileUuid(complianceProfile.getUuid());
        v1RuleAssoc.setResource(com.czertainly.api.model.core.auth.Resource.CERTIFICATE);
        v1RuleAssoc.setConnectorUuid(connectorV1.getUuid());
        v1RuleAssoc.setKind(KIND_V1);
        v1RuleAssoc.setComplianceRuleUuid(complianceV1RuleUuid);
        complianceProfileRuleRepository.save(v1RuleAssoc);

        var v2RuleAssoc = new ComplianceProfileRule();
        v2RuleAssoc.setComplianceProfile(complianceProfile);
        v2RuleAssoc.setComplianceProfileUuid(complianceProfile.getUuid());
        v2RuleAssoc.setResource(Resource.CRYPTOGRAPHIC_KEY);
        v2RuleAssoc.setConnectorUuid(connectorV2.getUuid());
        v2RuleAssoc.setKind(KIND_V2);
        v2RuleAssoc.setComplianceRuleUuid(complianceV2RuleKeyUuid);
        complianceProfileRuleRepository.save(v2RuleAssoc);

        var v2GroupAssoc = new ComplianceProfileRule();
        v2GroupAssoc.setComplianceProfile(complianceProfile);
        v2GroupAssoc.setComplianceProfileUuid(complianceProfile.getUuid());
        v2GroupAssoc.setResource(com.czertainly.api.model.core.auth.Resource.CERTIFICATE);
        v2GroupAssoc.setConnectorUuid(connectorV2.getUuid());
        v2GroupAssoc.setKind(KIND_V2);
        v2GroupAssoc.setComplianceGroupUuid(complianceV2Group2Uuid);
        complianceProfileRuleRepository.save(v2GroupAssoc);

        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/compliance".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "rules": [
                                    {
                                      "uuid": "%s",
                                      "name": "Rule1",
                                      "status": "nok"
                                    },
                                    {
                                      "uuid": "%s",
                                      "name": "Rule2",
                                      "status": "na"
                                    }
                                  ]
                                }
                                """.formatted(complianceV2RuleUuid, complianceV2Rule2Uuid)))
        );

        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/complianceProvider/%s/compliance".formatted(KIND_V1)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": "nok",
                                  "rules": [
                                    {
                                      "uuid": "%s",
                                      "name": "Rule1-V1",
                                      "status": "na"
                                    }
                                  ]
                                }
                                """.formatted(complianceV1RuleUuid)))
        );

        // create and persist a certificate subject that belongs to the seeded RA profile
        var certificateChainInfo = CertificateGeneratorHelper.generateCertificateWithIssuer(KeyAlgorithm.RSA, "CN=Test-Ca", "CN=Test-EndEntity", null);
        UploadCertificateRequestDto uploadRequestDto = new UploadCertificateRequestDto();
        uploadRequestDto.setCertificate(certificateChainInfo.getCaCertificateBase64Encoded());
        var certificateDto = certificateService.upload(uploadRequestDto, true);
        uploadRequestDto.setCertificate(certificateChainInfo.getEndEntityCertificateBase64Encoded());
        var certificate2Dto = certificateService.upload(uploadRequestDto, true);

        // run compliance check for the seeded profile (should include internal rules and both providers)
        List<SecuredUUID> uuids = List.of(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        complianceService.checkCompliance(uuids, Resource.CERTIFICATE, null);

        // reload certificate and assert compliance result was stored
        Certificate certificate = certificateRepository.findByUuid(UUID.fromString(certificateDto.getUuid())).orElseThrow();
        ComplianceCheckResultDto complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CERTIFICATE, UUID.fromString(certificateDto.getUuid()));
        Assertions.assertEquals(ComplianceStatus.NOT_CHECKED, complianceCheckResult.getStatus(), "Compliance result status should be Not checked");
        Assertions.assertEquals(ComplianceStatus.NOT_CHECKED, certificate.getComplianceStatus(), "Compliance status should be Not checked");

        complianceService.checkResourceObjectCompliance(Resource.CERTIFICATE, UUID.fromString(certificate2Dto.getUuid()));
        Certificate certificate2 = certificateRepository.findByUuid(UUID.fromString(certificate2Dto.getUuid())).orElseThrow();
        complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CERTIFICATE, UUID.fromString(certificate2Dto.getUuid()));
        Assertions.assertEquals(ComplianceStatus.NOT_CHECKED, complianceCheckResult.getStatus(), "Compliance result status should be Not checked");
        Assertions.assertEquals(ComplianceStatus.NOT_CHECKED, certificate2.getComplianceStatus(), "Compliance status should be Not checked");

        certificate.setRaProfileUuid(associatedRaProfileUuid);
        certificateRepository.save(certificate);
        complianceService.checkCompliance(uuids, Resource.CERTIFICATE, null);
        complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CERTIFICATE, certificate.getUuid());
        Assertions.assertEquals(ComplianceStatus.NOK, complianceCheckResult.getStatus(), "Compliance result status should be Not Compliant");

        certificate2.setRaProfileUuid(associatedRaProfileUuid);
        certificateRepository.save(certificate2);
        complianceService.checkResourceObjectCompliance(Resource.CERTIFICATE, certificate2.getUuid());
        complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CERTIFICATE, certificate2.getUuid());
        Assertions.assertEquals(ComplianceStatus.NOK, complianceCheckResult.getStatus(), "Compliance result status should be Not Compliant");

        // Expect 4 failed rules: 1 internal, 1 v1 provider rule, 2 v2 provider rules (one group with two rules)
        Assertions.assertEquals(4, complianceCheckResult.getFailedRules().size(), "There should be 4 failed rules");

        complianceService.checkResourceObjectCompliance(Resource.RA_PROFILE, associatedRaProfileUuid);

        // check compliance of cryptographic key
        TokenInstanceReference token = new TokenInstanceReference();
        token.setName("Token");
        token.setAuthor("John Doe");
        token.setCreated(OffsetDateTime.now());
        token.setUpdated(OffsetDateTime.now());
        tokenRepository.save(token);

        TokenProfile tokenProfile = new TokenProfile();
        tokenProfile.setName("Token Profile 1");
        tokenProfile.setTokenInstanceReferenceUuid(token.getUuid());
        tokenProfile.setAuthor("John Doe");
        tokenProfile.setCreated(OffsetDateTime.now());
        tokenProfile.setUpdated(OffsetDateTime.now());
        tokenProfileRepository.save(tokenProfile);

        ComplianceProfileAssociation complianceProfileAssociation = new ComplianceProfileAssociation();
        complianceProfileAssociation.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileAssociation.setResource(Resource.TOKEN_PROFILE);
        complianceProfileAssociation.setObjectUuid(tokenProfile.getUuid());
        complianceProfileAssociationRepository.save(complianceProfileAssociation);

        CryptographicKey key = cryptographicKeyRepository.findWithAssociationsByUuid(certificate.getKeyUuid()).orElseThrow();
        CryptographicKeyItem keyItem = key.getItems().iterator().next();
        key.setTokenProfileUuid(tokenProfile.getUuid());
        cryptographicKeyRepository.save(key);

        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/compliance".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "rules": []
                                }
                                """))
        );


        complianceService.checkCompliance(uuids, Resource.CRYPTOGRAPHIC_KEY, null);
        complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CRYPTOGRAPHIC_KEY_ITEM, keyItem.getUuid());
        Assertions.assertEquals(ComplianceStatus.NA, complianceCheckResult.getStatus(), "Compliance status should be Not applicable due to incompatible attributes setting");

        RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
        requestAttributeDto.setUuid("7ed00886-e706-11ec-8fea-0242ac120002");
        requestAttributeDto.setName("KeyLength");
        requestAttributeDto.setContentType(AttributeContentType.INTEGER);
        requestAttributeDto.setContent(List.of(new IntegerAttributeContent(2048)));
        v2RuleAssoc.setAttributes(List.of(requestAttributeDto));
        complianceProfileRuleRepository.save(v2RuleAssoc);

        complianceService.checkResourceObjectCompliance(Resource.CRYPTOGRAPHIC_KEY, key.getUuid());
        complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CRYPTOGRAPHIC_KEY_ITEM, keyItem.getUuid());
        Assertions.assertEquals(ComplianceStatus.OK, complianceCheckResult.getStatus(), "Compliance status should be Compliant");

        complianceService.checkResourceObjectCompliance(Resource.CRYPTOGRAPHIC_KEY_ITEM, keyItem.getUuid());
        complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CRYPTOGRAPHIC_KEY_ITEM, keyItem.getUuid());
        Assertions.assertEquals(ComplianceStatus.OK, complianceCheckResult.getStatus(), "Compliance status should be Compliant");

        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/compliance".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "rules": [
                                    {
                                      "uuid": "%s",
                                      "name": "RuleKey",
                                      "status": "nok"
                                    }
                                  ]
                                }
                                """.formatted(complianceV2RuleKeyUuid)))
        );

        complianceService.checkResourceObjectCompliance(Resource.TOKEN_PROFILE, tokenProfile.getUuid());
        complianceCheckResult = complianceService.getComplianceCheckResult(Resource.CRYPTOGRAPHIC_KEY_ITEM, keyItem.getUuid());
        Assertions.assertEquals(ComplianceStatus.NOK, complianceCheckResult.getStatus(), "Compliance status should be Not Compliant");
    }

    @Test
    void testGetComplianceCheckResult_withInternalAndProviderRules() {
        // prepare compliance result with one failing internal rule and one failing provider rule (v2)
        ComplianceResultDto complianceResult = new ComplianceResultDto();
        complianceResult.setStatus(null);
        complianceResult.setTimestamp(null);

        ComplianceResultRulesDto internal = new ComplianceResultRulesDto();
        internal.setNotCompliant(new HashSet<>(List.of(internalRuleUuid)));
        internal.setNotApplicable(new HashSet<>());
        internal.setNotAvailable(new HashSet<>());
        complianceResult.setInternalRules(internal);

        ComplianceResultProviderRulesDto provider = new ComplianceResultProviderRulesDto();
        provider.setConnectorUuid(connectorV2.getUuid());
        provider.setKind(KIND_V2);
        provider.setNotCompliant(new HashSet<>(List.of(complianceV2RuleUuid)));
        provider.setNotApplicable(new HashSet<>());
        provider.setNotAvailable(new HashSet<>());
        complianceResult.setProviderRules(List.of(provider));

        // objectUuid only used for logging in implementation; use random
        UUID objectUuid = UUID.randomUUID();

        ComplianceCheckResultDto result = complianceService.getComplianceCheckResult(Resource.CERTIFICATE, objectUuid, complianceResult);

        Assertions.assertNotNull(result);
        // Expect two failed rules: one internal and one provider
        Assertions.assertEquals(2, result.getFailedRules().size());
        Set<UUID> found = new HashSet<>();
        for (ComplianceCheckRuleDto dto : result.getFailedRules()) {
            found.add(dto.getUuid());
        }
        Assertions.assertTrue(found.contains(internalRuleUuid));
        Assertions.assertTrue(found.contains(complianceV2RuleUuid));
    }

    @Test
    void testGetComplianceCheckResult_statusMapping_NOK_NA_NOT_AVAILABLE() {
        ComplianceResultDto complianceResult = new ComplianceResultDto();

        ComplianceResultProviderRulesDto provider = new ComplianceResultProviderRulesDto();
        provider.setConnectorUuid(connectorV2.getUuid());
        provider.setKind(KIND_V2);

        // use three different provider rules (v2)
        UUID rNok = complianceV2RuleUuid;
        UUID rNa = complianceV2Rule2Uuid;
        UUID rNotAvailable = UUID.randomUUID(); // not present in provider responses -> mapped as NOT_AVAILABLE

        provider.setNotCompliant(new HashSet<>(List.of(rNok)));
        provider.setNotApplicable(new HashSet<>(List.of(rNa)));
        provider.setNotAvailable(new HashSet<>(List.of(rNotAvailable)));
        complianceResult.setProviderRules(List.of(provider));

        ComplianceCheckResultDto result = complianceService.getComplianceCheckResult(Resource.CERTIFICATE, UUID.randomUUID(), complianceResult);

        Assertions.assertEquals(3, result.getFailedRules().size());

        Set<ComplianceRuleStatus> statuses = new HashSet<>();
        for (ComplianceCheckRuleDto dto : result.getFailedRules()) {
            statuses.add(dto.getStatus());
        }

        Assertions.assertTrue(statuses.contains(ComplianceRuleStatus.NOK));
        Assertions.assertTrue(statuses.contains(ComplianceRuleStatus.NA));
        Assertions.assertTrue(statuses.contains(ComplianceRuleStatus.NOT_AVAILABLE));
    }

    @Test
    void testMultipleProviders_resultsFromV1AndV2() {
        ComplianceResultDto complianceResult = new ComplianceResultDto();

        ComplianceResultProviderRulesDto p1 = new ComplianceResultProviderRulesDto();
        p1.setConnectorUuid(connectorV1.getUuid());
        p1.setKind(KIND_V1);
        p1.setNotCompliant(new HashSet<>(List.of(complianceV1RuleUuid)));
        p1.setNotApplicable(new HashSet<>());
        p1.setNotAvailable(new HashSet<>());

        ComplianceResultProviderRulesDto p2 = new ComplianceResultProviderRulesDto();
        p2.setConnectorUuid(connectorV2.getUuid());
        p2.setKind(KIND_V2);
        p2.setNotCompliant(new HashSet<>(List.of(complianceV2RuleUuid)));
        p2.setNotApplicable(new HashSet<>());
        p2.setNotAvailable(new HashSet<>());

        complianceResult.setProviderRules(List.of(p1, p2));

        ComplianceCheckResultDto result = complianceService.getComplianceCheckResult(Resource.CERTIFICATE, UUID.randomUUID(), complianceResult);

        // Expect two provider rules present in failed rules
        Set<UUID> found = new HashSet<>();
        for (ComplianceCheckRuleDto dto : result.getFailedRules()) {
            found.add(dto.getUuid());
        }
        Assertions.assertTrue(found.contains(complianceV1RuleUuid));
        Assertions.assertTrue(found.contains(complianceV2RuleUuid));
    }

    @Test
    void testCheckComplianceValidation() {
        // check validation of compliance check request
        List<SecuredUUID> uuids = List.of(SecuredUUID.fromUUID(UUID.randomUUID()));
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkComplianceValidation(uuids, null, "SOME_TYPE"), "Resource must be specified");
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkCompliance(uuids, null, "SOME_TYPE"), "Resource must be specified");
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkComplianceValidation(uuids, Resource.NONE, "SOME_TYPE"), "Resource cannot be NONE, has to be compliance subject or has compliance profiles");
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkCompliance(uuids, Resource.NONE, "SOME_TYPE"), "Resource cannot be NONE, has to be compliance subject or has compliance profiles");
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkComplianceValidation(uuids, Resource.CERTIFICATE, "SOME_TYPE"), "Resource CERTIFICATE support only types from CertificateType enum");
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkCompliance(uuids, Resource.CERTIFICATE, "SOME_TYPE"), "Resource CERTIFICATE support only types from CertificateType enum");
        Assertions.assertThrows(NotFoundException.class, () -> complianceService.checkComplianceValidation(uuids, Resource.CERTIFICATE, CertificateType.X509.getCode()), "No compliance profile found with specified UUID");

        // check resource objects compliance validation
        List<UUID> objectUuids = List.of(UUID.randomUUID());
        UUID objectUuid = objectUuids.getFirst();
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkResourceObjectsComplianceValidation(Resource.NONE, objectUuids), "Resource cannot be NONE, has to be compliance subject or has compliance profiles");
        Assertions.assertThrows(ValidationException.class, () -> complianceService.checkResourceObjectCompliance(Resource.NONE, objectUuid), "Resource must be specified");
        Assertions.assertThrows(NotFoundException.class, () -> complianceService.checkResourceObjectsComplianceValidation(Resource.RA_PROFILE, objectUuids), "No RA Profile found with specified UUID");
    }
}
