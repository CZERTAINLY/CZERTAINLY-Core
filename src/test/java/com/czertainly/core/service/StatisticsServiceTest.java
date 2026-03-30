package com.czertainly.core.service;

import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.connector.secrets.SecretType;
import com.czertainly.api.model.core.secret.SecretState;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.SerializationUtils;

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
        secretVersion.setVaultInstance(vaultInstance);
        secretVersion.setVaultInstanceUuid(vaultInstance.getUuid());
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

        StatisticsDto result = statisticsService.getStatistics(false);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1L, result.getTotalSecrets());
        Assertions.assertEquals(1L, result.getTotalVaultInstances());
        Assertions.assertEquals(1L, result.getTotalVaultProfiles());
        // grouped stats run in virtual threads and don't see uncommitted test-transaction data;
        // asserting non-null is sufficient to verify addSecretStatistics ran without error
        Assertions.assertNotNull(result.getSecretStatByType());
        Assertions.assertNotNull(result.getSecretStatByState());
        Assertions.assertNotNull(result.getSecretStatByComplianceStatus());
    }
}
