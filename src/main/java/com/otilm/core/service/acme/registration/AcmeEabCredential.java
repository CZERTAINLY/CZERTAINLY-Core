package com.otilm.core.service.acme.registration;

import com.nimbusds.jose.util.Base64URL;
import com.otilm.core.service.registration.RegistrationResolver;
import com.otilm.core.util.RandomUtil;

/**
 * The External Account Binding credential of an ACME-completable pre-registration. The registration challenge is a
 * random base64url text; its UTF-8 bytes are the MAC key (the rule every protocol shares, see
 * {@link RegistrationResolver#macKey}), and the credential handed to the ACME client is that key base64url-encoded, the
 * form clients decode into {@code --eab-hmac-key} bytes.
 */
public final class AcmeEabCredential {

    /** 32 random bytes encode to 43 base64url characters, so the UTF-8 key text exceeds the 256-bit HS256 minimum. */
    private static final int RANDOM_BYTES = 32;

    private AcmeEabCredential() {
    }

    /** A fresh random challenge text, stored as the registration challenge. */
    public static String generateChallenge() {
        return RandomUtil.generateRandomNonceBase64Url(RANDOM_BYTES);
    }

    /** The {@code eabHmacKey} value for a challenge text, shown once at pre-registration. */
    public static String toEabHmacKey(String challenge) {
        return Base64URL.encode(RegistrationResolver.macKey(challenge)).toString();
    }
}
