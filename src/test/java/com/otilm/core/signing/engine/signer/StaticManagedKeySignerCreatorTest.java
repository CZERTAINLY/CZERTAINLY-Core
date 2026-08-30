package com.otilm.core.signing.engine.signer;

import com.otilm.api.model.common.enums.cryptography.KeyAlgorithm;
import com.otilm.core.model.crypto.CryptographicKeyItemModelFixtures;
import com.otilm.core.model.signing.SigningCertificateBuilder;
import com.otilm.core.model.signing.resolved.ResolvedStaticKeyManagedSigning;
import com.otilm.core.service.CryptographicOperationInternalService;
import com.otilm.core.signing.engine.error.SigningEngineException;
import com.otilm.core.signing.engine.error.SigningEngineFailure;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class StaticManagedKeySignerCreatorTest {

    @Mock
    private CryptographicOperationInternalService cryptographicOperationService;

    private StaticManagedKeySignerCreator creator;

    @BeforeEach
    void createSignerCreator() {
        creator = new StaticManagedKeySignerCreator(cryptographicOperationService);
    }

    // ── Supports ──────────────────────────────────────────────────────────────

    @Nested
    class Supports {

        @Test
        void returnsTrue_forResolvedStaticKeyManagedSigning() {
            // given
            ResolvedStaticKeyManagedSigning scheme = new ResolvedStaticKeyManagedSigning(
                    SigningCertificateBuilder.valid(), List.of(), null, List.of());

            // when / then
            assertThat(creator.supports(scheme)).isTrue();
        }
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void throwsMisconfigured_whenCertificateHasNoKey() {
            // given — the certificate is not backed by a managed cryptographic key (no key UUID)
            ResolvedStaticKeyManagedSigning scheme = new ResolvedStaticKeyManagedSigning(
                    SigningCertificateBuilder.aSigningCertificate().withoutKey().build(), List.of(), null, List.of());

            // when / then
            assertThatThrownBy(() -> creator.create(scheme))
                    .isInstanceOf(SigningEngineException.class)
                    .satisfies(ex -> assertThat(((SigningEngineException) ex).failure())
                            .isEqualTo(SigningEngineFailure.MISCONFIGURED));
        }

        @Test
        void throwsMisconfigured_whenKeyHasNoPrivateKeyItem() {
            // given — the key only holds a public key item (no private key to sign with)
            ResolvedStaticKeyManagedSigning scheme = new ResolvedStaticKeyManagedSigning(
                    SigningCertificateBuilder.valid(),
                    List.of(CryptographicKeyItemModelFixtures.publicKey(KeyAlgorithm.RSA)), null, List.of());

            // when / then
            assertThatThrownBy(() -> creator.create(scheme))
                    .isInstanceOf(SigningEngineException.class)
                    .satisfies(ex -> assertThat(((SigningEngineException) ex).failure())
                            .isEqualTo(SigningEngineFailure.MISCONFIGURED));
        }

        /**
         * An operator can pick a digest the platform has no signature-algorithm entry for. Without the translation the
         * enum's unchecked throw reaches the engine's catch-all and is logged as a platform fault, hiding a Signing
         * Profile the operator could fix.
         */
        @Test
        void throwsMisconfigured_whenTheKeyAndAttributesNameNoKnownSignatureAlgorithm() {
            // given — no signing attributes at all, so no digest or RSA scheme can be read for an RSA key
            ResolvedStaticKeyManagedSigning scheme = new ResolvedStaticKeyManagedSigning(
                    SigningCertificateBuilder.valid(),
                    List
                            .of(CryptographicKeyItemModelFixtures.activeSigningPrivateKey(KeyAlgorithm.RSA),
                                    CryptographicKeyItemModelFixtures.publicKey(KeyAlgorithm.RSA)),
                    null, List.of());

            // when / then
            assertThatThrownBy(() -> creator.create(scheme))
                    .isInstanceOf(SigningEngineException.class)
                    .satisfies(ex -> assertThat(((SigningEngineException) ex).failure())
                            .isEqualTo(SigningEngineFailure.MISCONFIGURED));
        }

        @Test
        void throwsMisconfigured_whenKeyHasNoPublicKeyItem() {
            // given — only a private (RSA) key item is present; the signer still requires a public key item
            // even for classical algorithms, so this must fail (regression guard for the record-based path)
            ResolvedStaticKeyManagedSigning scheme = new ResolvedStaticKeyManagedSigning(
                    SigningCertificateBuilder.valid(),
                    List.of(CryptographicKeyItemModelFixtures.activeSigningPrivateKey(KeyAlgorithm.RSA)), null,
                    List.of());

            // when / then
            assertThatThrownBy(() -> creator.create(scheme))
                    .isInstanceOf(SigningEngineException.class)
                    .satisfies(ex -> assertThat(((SigningEngineException) ex).failure())
                            .isEqualTo(SigningEngineFailure.MISCONFIGURED));
        }
    }
}
