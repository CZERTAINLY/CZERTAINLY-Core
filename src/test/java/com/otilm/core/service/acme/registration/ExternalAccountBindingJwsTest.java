package com.otilm.core.service.acme.registration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.otilm.api.model.core.acme.ExternalAccountBinding;
import com.otilm.core.service.registration.RegistrationResolver;
import com.otilm.core.service.registration.UnusableRegistrationChallengeException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalAccountBindingJwsTest {

    private static final String URL = "https://acme.example/api/acme/profile/new-account";
    private static final UUID KID = UUID.fromString("f2bfe4a1-b834-4f0c-9bd6-e0b323f8a5f8");

    private ECKey accountKey;
    private String challenge;
    private byte[] macKey;

    @BeforeEach
    void setUp() throws JOSEException {
        accountKey = new ECKeyGenerator(Curve.P_256).generate();
        challenge = AcmeEabCredential.generateChallenge();
        macKey = RegistrationResolver.macKey(challenge);
    }

    @Test
    void generatedChallengeYieldsAnHs256KeyAndTheClientCredentialDecodesToIt() {
        assertTrue(macKey.length >= 32, "the challenge text must be at least 256 bits of key material");
        assertArrayEquals(macKey, new Base64URL(AcmeEabCredential.toEabHmacKey(challenge)).decode(),
                "the eabHmacKey a client decodes must be the same bytes the platform MACs with");
    }

    @Test
    void wellFormedBindingParsesAndVerifies() throws JOSEException {
        ExternalAccountBindingJws jws = ExternalAccountBindingJws
                .parse(EabTestUtil.build(KID, URL, accountKey, macKey));

        assertNotNull(jws);
        assertEquals(KID, jws.registrationUuid());
        assertTrue(jws.isForUrl(URL));
        assertTrue(jws.bindsAccountKey(accountKey.toPublicJWK()));
        assertTrue(jws.verify(macKey));
    }

    @Test
    void wrongKeyDoesNotVerify() throws JOSEException {
        ExternalAccountBindingJws jws = ExternalAccountBindingJws
                .parse(EabTestUtil.build(KID, URL, accountKey, macKey));

        assertFalse(jws.verify(RegistrationResolver.macKey(AcmeEabCredential.generateChallenge())));
    }

    @Test
    void keyTooShortForHs256IsAConfigurationFaultNotAMismatch() throws JOSEException {
        ExternalAccountBindingJws jws = ExternalAccountBindingJws
                .parse(EabTestUtil.build(KID, URL, accountKey, macKey));
        byte[] shortKey = RegistrationResolver.macKey("device-7-secret");

        assertThrows(UnusableRegistrationChallengeException.class, () -> jws.verify(shortKey));
    }

    @Test
    void otherUrlAndOtherAccountKeyDoNotBind() throws JOSEException {
        ExternalAccountBindingJws jws = ExternalAccountBindingJws
                .parse(EabTestUtil.build(KID, URL, accountKey, macKey));

        assertFalse(jws.isForUrl(URL + "/"));
        assertFalse(jws.bindsAccountKey(new ECKeyGenerator(Curve.P_256).generate().toPublicJWK()));
        assertFalse(jws.bindsAccountKey(null));
    }

    @Test
    void nonUuidOrMissingKidHasNoRegistrationReference() throws JOSEException {
        assertNull(ExternalAccountBindingJws
                .parse(EabTestUtil.build(JWSAlgorithm.HS256, "not-a-uuid", URL, accountKey, macKey))
                .registrationUuid());
        assertNull(ExternalAccountBindingJws
                .parse(EabTestUtil.build(JWSAlgorithm.HS256, null, URL, accountKey, macKey))
                .registrationUuid());
    }

    @Test
    void missingUrlIsNotForAnyUrl() throws JOSEException {
        assertFalse(ExternalAccountBindingJws
                .parse(EabTestUtil.build(JWSAlgorithm.HS256, KID.toString(), null, accountKey, macKey))
                .isForUrl(URL));
    }

    @Test
    void onlyHs256IsAccepted() throws JOSEException {
        byte[] longKey = new byte[48];
        assertNull(ExternalAccountBindingJws
                .parse(EabTestUtil.build(JWSAlgorithm.HS384, KID.toString(), URL, accountKey, longKey)));
    }

    @Test
    void structurallyInvalidBindingsDoNotParse() throws JOSEException {
        assertNull(ExternalAccountBindingJws.parse(null));

        ExternalAccountBinding missingSignature = EabTestUtil.build(KID, URL, accountKey, macKey);
        missingSignature.setSignature(null);
        assertNull(ExternalAccountBindingJws.parse(missingSignature));

        ExternalAccountBinding garbageHeader = EabTestUtil.build(KID, URL, accountKey, macKey);
        garbageHeader.setProtectedHeader("!!not-base64url!!");
        assertNull(ExternalAccountBindingJws.parse(garbageHeader));

        ExternalAccountBinding payloadNotJwk = EabTestUtil.build(KID, URL, accountKey, macKey);
        payloadNotJwk.setPayload(Base64URL.encode("{\"hello\":\"world\"}").toString());
        assertNull(ExternalAccountBindingJws.parse(payloadNotJwk));
    }
}
