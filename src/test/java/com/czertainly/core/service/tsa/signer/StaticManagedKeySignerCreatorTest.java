package com.czertainly.core.service.tsa.signer;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.model.signing.scheme.DelegatedSigning;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import com.czertainly.core.service.CryptographicOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.czertainly.core.dao.entity.CertificateBuilder.aCertificate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StaticManagedKeySignerCreatorTest {

    @Mock
    private CryptographicOperationService cryptographicOperationService;

    private StaticManagedKeySignerCreator creator;

    @BeforeEach
    void setUp() {
        creator = new StaticManagedKeySignerCreator(cryptographicOperationService);
    }

    // ── supports ──────────────────────────────────────────────────────────────

    @Test
    void supports_returnsTrue_forStaticKeyManagedSigning() {
        // given
        StaticKeyManagedSigning scheme = new StaticKeyManagedSigning(aCertificate().build(), List.of());

        // when / then
        assertThat(creator.supports(scheme)).isTrue();
    }

    @Test
    void supports_returnsFalse_forOtherSchemes() {
        // given
        SigningSchemeModel otherScheme = mock(DelegatedSigning.class);

        // when / then
        assertThat(creator.supports(otherScheme)).isFalse();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_throwsSystemFailure_whenCertificateHasNoKey() {
        // given — the certificate is not backed by a managed cryptographic key
        StaticKeyManagedSigning scheme = new StaticKeyManagedSigning(
                aCertificate().withoutKey().build(), List.of());

        // when / then
        assertThatThrownBy(() -> creator.create(scheme))
                .isInstanceOf(TspException.class)
                .satisfies(ex -> assertThat(((TspException) ex).getFailureInfo())
                        .isEqualTo(TspFailureInfo.SYSTEM_FAILURE));
    }

    @Test
    void create_throwsSystemFailure_whenKeyHasNoPrivateKeyItem() {
        // given — the key only holds a public key item (no private key to sign with)
        CryptographicKey key = new CryptographicKey();
        CryptographicKeyItem publicItem = new CryptographicKeyItem();
        publicItem.setType(KeyType.PUBLIC_KEY);
        key.setItems(Set.of(publicItem));

        Certificate cert = new Certificate();
        cert.setKey(key);

        StaticKeyManagedSigning scheme = new StaticKeyManagedSigning(cert, List.of());

        // when / then
        assertThatThrownBy(() -> creator.create(scheme))
                .isInstanceOf(TspException.class)
                .satisfies(ex -> assertThat(((TspException) ex).getFailureInfo())
                        .isEqualTo(TspFailureInfo.SYSTEM_FAILURE));
    }

    @Test
    void create_throwsSystemFailure_whenKeyHasNoPublicKeyItem() {
        // given — CertificateBuilder.valid() produces a key with only a private key item;
        // the algorithm resolver requires the public key data to determine the signature algorithm
        StaticKeyManagedSigning scheme = new StaticKeyManagedSigning(
                aCertificate().build(), List.of());

        // when / then
        assertThatThrownBy(() -> creator.create(scheme))
                .isInstanceOf(TspException.class)
                .satisfies(ex -> assertThat(((TspException) ex).getFailureInfo())
                        .isEqualTo(TspFailureInfo.SYSTEM_FAILURE));
    }
}
