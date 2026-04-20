package com.czertainly.core.service;

import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.connector.secrets.SecretType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.secret.SecretState;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.opa.dto.OpaObjectAccessResult;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.SerializationUtils;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@Transactional
@Rollback
class StatisticsServiceTest extends BaseSpringBootTest {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private VaultInstanceRepository vaultInstanceRepository;

    @Autowired
    private VaultProfileRepository vaultProfileRepository;

    @Autowired
    private SecretVersionRepository secretVersionRepository;

    @Autowired
    private SecretRepository secretRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private OwnerAssociationRepository ownerAssociationRepository;

    @Test
    void testGetStatistics() {
        StatisticsDto result = statisticsService.getStatistics(false);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0L, result.getTotalCertificates());
        Assertions.assertEquals(0L, result.getTotalGroups());
        Assertions.assertEquals(0L, result.getTotalSecrets());
        Assertions.assertEquals(0L, result.getTotalVaultInstances());
        Assertions.assertEquals(0L, result.getTotalVaultProfiles());
        Assertions.assertTrue(result.getSecretStatByType().isEmpty());
    }

    @Test
    void testGetStatistics_withLowPrivilegesCertificates() {
        Certificate certificateNotOwned = new Certificate();
        certificateNotOwned.setValidationStatus(CertificateValidationStatus.VALID);
        certificateRepository.save(certificateNotOwned);
        Certificate certificateOwned = new Certificate();
        certificateOwned.setValidationStatus(CertificateValidationStatus.FAILED);
        certificateRepository.save(certificateOwned);
        OwnerAssociation ownerAssociation = new OwnerAssociation();
        ownerAssociation.setObjectUuid(certificateOwned.getUuid());
        ownerAssociation.setResource(Resource.CERTIFICATE);
        NameAndUuidDto user = AuthHelper.getUserIdentification();
        ownerAssociation.setOwnerUsername(user.getName());
        ownerAssociation.setOwnerUuid(UUID.fromString(user.getUuid()));
        ownerAssociationRepository.save(ownerAssociation);
        certificateOwned.setOwner(ownerAssociation);
        certificateRepository.save(certificateOwned);

        OpaResourceAccessResult resourceAccessNotAllowed = new OpaResourceAccessResult(false, List.of());
        // By default, reject all
        Mockito.when(
                opaClient.checkResourceAccess(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())
        ).thenReturn(resourceAccessNotAllowed);
        OpaObjectAccessResult opaObjectAccessResult = new OpaObjectAccessResult();
        opaObjectAccessResult.setAllowedObjects(List.of());
        opaObjectAccessResult.setForbiddenObjects(List.of(certificateNotOwned.getUuid().toString(), certificateOwned.getUuid().toString()));
        Mockito.when(
                        opaClient.checkObjectAccess(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(opaObjectAccessResult);

        // commit setup data so virtual-thread queries in addCertificateStatistics can see it
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StatisticsDto result = statisticsService.getStatistics(false);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1L, result.getTotalCertificates());
        Assertions.assertFalse(result.getCertificateStatByComplianceStatus().isEmpty());
    }

    @Test
    void testGetStatistics_oneGroup() {
        groupRepository.save(new Group());

        StatisticsDto result = statisticsService.getStatistics(false);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0L, result.getTotalCertificates());
        Assertions.assertEquals(1L, result.getTotalGroups());
    }

    @Test
    void testGetStatistics_withSecretAndVaultEntities() throws Exception {
        VaultInstance vaultInstance = new VaultInstance();
        vaultInstance.setName("testInstance");
        vaultInstanceRepository.save(vaultInstance);

        VaultProfile vaultProfile = new VaultProfile();
        vaultProfile.setName("testProfile");
        vaultProfile.setVaultInstance(vaultInstance);
        vaultProfile.setVaultInstanceUuid(vaultInstance.getUuid());
        vaultProfile.setEnabled(true);
        vaultProfileRepository.save(vaultProfile);

        SecretVersion secretVersion = new SecretVersion();
        secretVersion.setVersion(1);
        secretVersion.setVaultProfile(vaultProfile);
        secretVersion.setFingerprint(CertificateUtil.getThumbprint(SerializationUtils.serialize("test")));
        secretVersionRepository.save(secretVersion);

        Secret secret = new Secret();
        secret.setName("testSecret");
        secret.setType(SecretType.BASIC_AUTH);
        secret.setState(SecretState.ACTIVE);
        secret.setSourceVaultProfile(vaultProfile);
        secret.setSourceVaultProfileUuid(vaultProfile.getUuid());
        secret.setEnabled(true);
        secret.setLatestVersion(secretVersion);
        secretRepository.save(secret);

        secretVersion.setSecretUuid(secret.getUuid());
        secretVersionRepository.save(secretVersion);

        // commit setup data so virtual-thread queries in addSecretStatistics can see it
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StatisticsDto result = statisticsService.getStatistics(false);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1L, result.getTotalSecrets());
        Assertions.assertEquals(1L, result.getTotalVaultInstances());
        Assertions.assertEquals(1L, result.getTotalVaultProfiles());
        Assertions.assertEquals(1, result.getSecretStatByType().size());
        Assertions.assertEquals(1L, result.getSecretStatByType().get(SecretType.BASIC_AUTH.getCode()));
        Assertions.assertEquals(1, result.getSecretStatByState().size());
        Assertions.assertEquals(1L, result.getSecretStatByState().get(SecretState.ACTIVE.getCode()));
        Assertions.assertEquals(1, result.getSecretStatByComplianceStatus().size());
    }
}
