package com.czertainly.core;

import com.czertainly.api.model.core.audit.AuditLogFilter;
import com.czertainly.api.model.core.audit.ExportResultDto;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.util.AcmePublicKeyProcessor;
import com.czertainly.core.util.AcmeRandomGeneratorAndValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

@SpringBootTest
public class AcmeUtilTest {

    @Autowired
    private AuditLogService auditLogService;

    @Test
    public void testNonceGeneration(){
        String nonce = AcmeRandomGeneratorAndValidator.generateNonce();
        Assertions.assertNotNull(nonce);
        Assertions.assertTrue(nonce.length() > 3);
    }

    @Test
    public void testRandomIdGeneration(){
        String nonce = AcmeRandomGeneratorAndValidator.generateRandomId();
        Assertions.assertNotNull(nonce);
        Assertions.assertTrue(nonce.length() > 3);
    }

    @Test
    public void testRandomTokenForValidation() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        String nonce = AcmeRandomGeneratorAndValidator.generateRandomTokenForValidation(publicKey);
        Assertions.assertNotNull(nonce);
        Assertions.assertTrue(nonce.length() > 3);
    }

    @Test
    public void testPublicKeyProcessing() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        String publicKeyString = AcmePublicKeyProcessor.publicKeyPemStringFromObject(publicKey);
        PublicKey pubkey1 = AcmePublicKeyProcessor.publicKeyObjectFromString(publicKeyString);
        Assertions.assertEquals(pubkey1, publicKey);
    }
}
