package com.czertainly.core.util;

import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.stream.Stream;

public class CertificateTestData {

    public static Stream<Arguments> provideCmpAcceptableTestData() {
        return Stream.of(
                // 1. Certificate that should be accepted (RSA)
                Arguments.of("RSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        true),

                // 2. Certificate that should be accepted (ECDSA)
                Arguments.of("ECDSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        true),

                // 3. Certificate that should be accepted (EXPIRING)
                Arguments.of("Expiring Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.EXPIRING, false,
                        true),

                // 4. Certificate with no key (should be ignored)
                Arguments.of("No Key Cert",
                        null, null, null,
                        null, null, null, null,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false),

                // 5. Archived certificate (should be ignored)
                Arguments.of("Archived Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, true,
                        false),

                // 6. Certificate with wrong state (should be ignored)
                Arguments.of("Wrong State Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.PENDING_APPROVAL, CertificateValidationStatus.VALID, false,
                        false),

                // 7. Certificate with wrong validation status (should be ignored)
                Arguments.of("Invalid Status Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.REVOKED, false,
                        false),

                // 8. Certificate with no private key (should be ignored)
                Arguments.of("No Private Key Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        null, null, null, null,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false),

                // 9. Certificate with inactive private key (should be ignored)
                Arguments.of("Inactive Private Key Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.SIGN), KeyState.DEACTIVATED,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false),

                // 10. Certificate with private key missing SIGN usage (should be ignored)
                Arguments.of("No Sign Usage Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false),

                // 11. Certificate with a Falcon key (should be accepted)
                Arguments.of("Falcon Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.FALCON, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.FALCON, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        true),

                // 12. Public key only (should be rejected)
                Arguments.of("Public Key Only",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        null, null, null, null,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false)
        );
    }

    public static Stream<Arguments> provideScepCaCertificateTestData() {
        return Stream.of(
                // 1. Certificate that should be accepted (RSA)
                Arguments.of("RSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT, KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, true),

                // 2. Certificate that should be accepted (ECDSA)
                Arguments.of("ECDSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, true),

                // 3. Certificate with no key (should be ignored)
                Arguments.of("No Key Cert",
                        null, null, null,
                        null, null, null, null,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 4. Archived certificate (should be ignored)
                Arguments.of("Archived Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT, KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, true,
                        false, false),

                // 5. Certificate with wrong state (should be ignored)
                Arguments.of("Wrong State Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT, KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.PENDING_APPROVAL, CertificateValidationStatus.VALID, false,
                        false, false),

                // 6. Certificate with a deactivated private RSA key
                Arguments.of("Deactivated RSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT, KeyUsage.SIGN), KeyState.DEACTIVATED,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 7. Certificate with a wrong private RSA key usage
                Arguments.of("Wrong usage RSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.WRAP, KeyUsage.UNWRAP), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 8. Certificate with an invalid RSA key type
                Arguments.of("Secret RSA Cert",
                        KeyType.SECRET_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        null, null, null, null,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 9. Certificate with a non RSA/ECDSA key algorithm
                Arguments.of("Falcon Cert",
                        null, null, null,
                        KeyType.PRIVATE_KEY, KeyAlgorithm.FALCON, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 10. Certificate with a wrong key usage for ECDSA public key
                Arguments.of("Wrong usage public ECDSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.WRAP),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 11. Certificate with a deactivated private ECDSA key
                Arguments.of("Deactivated ECDSA Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.SIGN), KeyState.DEACTIVATED,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 12. Certificate with a wrong private ECDSA key usage
                Arguments.of("Wrong usage private Ecdsa Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.WRAP), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 13. Certificate with an invalid Ecdsa key type
                Arguments.of("Secret Ecdsa Cert",
                        KeyType.SECRET_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        null, null, null, null,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false),

                // 14. Test with intuneEnabled = true (only RSA acceptable)
                Arguments.of("RSA Cert Intune",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT, KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        true, true),

                // 15. Test with intuneEnabled = true (ECDSA not acceptable)
                Arguments.of("ECDSA Cert Intune",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.ECDSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        true, false),

                // 16. Certificate with expiring status (should be accepted)
                Arguments.of("Expiring Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT, KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.EXPIRING, false,
                        false, true),

                // 17. Certificate with invalid status (should be ignored)
                Arguments.of("Invalid Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.DECRYPT, KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.INVALID, false,
                        false, false),

                // 18. RSA Certificate with missing usages (should be ignored)
                Arguments.of("Wrong Usage Cert",
                        KeyType.PUBLIC_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.VERIFY),
                        KeyType.PRIVATE_KEY, KeyAlgorithm.RSA, List.of(KeyUsage.SIGN), KeyState.ACTIVE,
                        CertificateState.ISSUED, CertificateValidationStatus.VALID, false,
                        false, false)
        );
    }
}
