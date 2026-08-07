package com.otilm.core.integration.dao.repository;

import com.otilm.api.model.core.certificate.CertificateState;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.CertificateRegistrationAuthorization;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.entity.RegistrationState;
import com.otilm.core.dao.repository.CertificateRegistrationAuthorizationRepository;
import com.otilm.core.dao.repository.CertificateRepository;
import com.otilm.core.dao.repository.RaProfileRepository;
import com.otilm.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.otilm.core.util.builders.CertificateBuilder.aCertificate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the durable registration-authorization persistence against a real PostgreSQL.
 */
class CertificateRegistrationAuthorizationRepositoryITest extends BaseSpringBootTest {

    @Autowired
    private CertificateRegistrationAuthorizationRepository authorizationRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID certificateUuid;

    @BeforeEach
    void createCertificate() {
        certificateUuid = certificateRepository.save(aCertificate().build()).getUuid();
    }

    private CertificateRegistrationAuthorization persistAuthorization() {
        CertificateRegistrationAuthorization authorization = new CertificateRegistrationAuthorization();
        authorization.setCertificateUuid(certificateUuid);
        authorization.setChallenge("v1|ciphertext|salt|1000");
        authorization.setExpiresAt(OffsetDateTime.now().plusDays(1));
        authorization.setState(RegistrationState.ACTIVE);
        return authorizationRepository.save(authorization);
    }

    @Test
    void findsOnlyRegisteredCertificatesWithActiveAuthorizationUnderRaProfile() {
        UUID raProfileUuid = persistRaProfile();
        UUID otherRaProfileUuid = persistRaProfile();

        Certificate match = persistCertificate(CertificateState.REGISTERED, raProfileUuid);
        persistAuthorizationFor(match.getUuid(), RegistrationState.ACTIVE);

        Certificate lockedAuthorization = persistCertificate(CertificateState.REGISTERED, raProfileUuid);
        persistAuthorizationFor(lockedAuthorization.getUuid(), RegistrationState.LOCKED);

        // REGISTERED without any authorization row: not challenge-protected, must not match.
        persistCertificate(CertificateState.REGISTERED, raProfileUuid);

        Certificate issued = persistCertificate(CertificateState.ISSUED, raProfileUuid);
        persistAuthorizationFor(issued.getUuid(), RegistrationState.ACTIVE);

        Certificate underOtherProfile = persistCertificate(CertificateState.REGISTERED, otherRaProfileUuid);
        persistAuthorizationFor(underOtherProfile.getUuid(), RegistrationState.ACTIVE);

        assertThat(certificateRepository.findRegisteredWithActiveRegistrationAuthorizationByRaProfileUuid(raProfileUuid))
                .extracting(Certificate::getUuid)
                .containsExactly(match.getUuid());
    }

    private UUID persistRaProfile() {
        RaProfile raProfile = new RaProfile();
        raProfile.setName("rp-" + UUID.randomUUID());
        raProfile.setEnabled(true);
        return raProfileRepository.save(raProfile).getUuid();
    }

    private Certificate persistCertificate(CertificateState state, UUID raProfileUuid) {
        Certificate certificate = aCertificate().withState(state).build();
        certificate.setRaProfileUuid(raProfileUuid);
        return certificateRepository.save(certificate);
    }

    private void persistAuthorizationFor(UUID forCertificateUuid, RegistrationState state) {
        CertificateRegistrationAuthorization authorization = new CertificateRegistrationAuthorization();
        authorization.setCertificateUuid(forCertificateUuid);
        authorization.setChallenge("v1|ciphertext|salt|1000");
        authorization.setExpiresAt(OffsetDateTime.now().plusDays(1));
        authorization.setState(state);
        authorizationRepository.save(authorization);
    }

    @Test
    void findByCertificateUuidReturnsThePersistedRow() {
        UUID persistedUuid = persistAuthorization().getUuid();

        CertificateRegistrationAuthorization found =
                authorizationRepository.findByCertificateUuid(certificateUuid).orElseThrow();

        assertThat(found.getUuid()).isEqualTo(persistedUuid);
        assertThat(found.getCertificateUuid()).isEqualTo(certificateUuid);
        assertThat(found.getState()).isEqualTo(RegistrationState.ACTIVE);
    }

    @Test
    void lockedFinderReturnsRowInsideTransaction() {
        persistAuthorization();

        // SELECT ... FOR UPDATE requires an active transaction.
        CertificateRegistrationAuthorization locked = new TransactionTemplate(transactionManager).execute(
                status -> authorizationRepository.findAndLockByCertificateUuid(certificateUuid).orElseThrow());

        assertThat(locked).isNotNull();
        assertThat(locked.getCertificateUuid()).isEqualTo(certificateUuid);
    }

    @Test
    void deleteByCertificateUuidRemovesRow() {
        persistAuthorization();

        // The @Modifying delete needs an ambient transaction; the repository carries none by convention.
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> authorizationRepository.deleteByCertificateUuid(certificateUuid));

        assertThat(authorizationRepository.findByCertificateUuid(certificateUuid)).isEmpty();
    }
}
