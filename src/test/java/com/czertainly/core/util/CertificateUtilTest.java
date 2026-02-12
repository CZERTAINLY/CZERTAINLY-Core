package com.czertainly.core.util;

import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class CertificateUtilTest {

    private static final String VALID_SAN_STRING = "{\"dNSName\":[\"czertainly.com\"],\"directoryName\":[],\"ediPartyName\":[],\"iPAddress\":[\"192.168.10.10\"],\"otherName\":[\"1.2.3.4=example othername\"],\"registeredID\":[],\"rfc822Name\":[],\"uniformResourceIdentifier\":[],\"x400Address\":[]}";

    private static final Map<String, List<String>> VALID_SAN_MAP = Map.of(
            "registeredID", List.of(),
            "ediPartyName", List.of(),
            "iPAddress", List.of("192.168.10.10"),
            "x400Address", List.of(),
            "rfc822Name", List.of(),
            "otherName", List.of("1.2.3.4=example othername"),
            "dNSName", List.of("czertainly.com"),
            "uniformResourceIdentifier", List.of(),
            "directoryName", List.of()
    );

    @Test
    void testSerializeSans() {
        String result = CertificateUtil.serializeSans(VALID_SAN_MAP);
        Assertions.assertEquals(VALID_SAN_STRING, result);

        String nullResult = CertificateUtil.serializeSans(null);
        Assertions.assertEquals("{}", nullResult);

        String emptyResult = CertificateUtil.serializeSans(new HashMap<>());
        Assertions.assertEquals("{}", emptyResult);
    }

    @Test
    void testDeserializeSans() {
        Map<String, List<String>> result = CertificateUtil.deserializeSans(VALID_SAN_STRING);
        Assertions.assertEquals(VALID_SAN_MAP, result);

        Map<String, List<String>> emptyResult = CertificateUtil.deserializeSans(null);
        Assertions.assertTrue(emptyResult.isEmpty());

        Map<String, List<String>> emptyStringResult = CertificateUtil.deserializeSans("");
        Assertions.assertTrue(emptyStringResult.isEmpty());
    }

    @Test
    void testInvalidDeserializeSans() {
        String invalidJson = "{invalid json}";
        Assertions.assertThrows(IllegalStateException.class, () -> CertificateUtil.deserializeSans(invalidJson));
    }

    @Test
    void testInvalidSerializeSans() {
        Map<String, List<String>> invalidMap = new HashMap<>();
        invalidMap.put("invalidKey", null);

        Assertions.assertThrows(IllegalStateException.class, () -> CertificateUtil.serializeSans(invalidMap));
    }

    @Test
    void testParseHybridCertificate() throws IOException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException, OperatorCreationException, CertificateException {
        X509Certificate certificate = CertificateTestUtil.createHybridCertificate();

        Certificate certificateEntity = new Certificate();
        CertificateUtil.prepareIssuedCertificate(certificateEntity, certificate);
        Assertions.assertEquals("ML-DSA-44", certificateEntity.getAltSignatureAlgorithm());

    }

    @Test
    void testIsValidationEnabled() {
        Certificate certificate = new Certificate();
        certificate.setArchived(true);
        Assertions.assertFalse(CertificateUtil.isValidationEnabled(certificate, null));
    }

    @ParameterizedTest
    @MethodSource("com.czertainly.core.util.CertificateTestData#provideCmpAcceptableTestData")
    public void testIsCertificateCmpAcceptable(
            String testCaseName,
            KeyType publicKeyType, KeyAlgorithm publicKeyAlgorithm, List<KeyUsage> publicKeyUsages,
            KeyType privateKeyType, KeyAlgorithm privateKeyAlgorithm, List<KeyUsage> privateKeyUsages, KeyState privateKeyState,
            CertificateState certificateState, CertificateValidationStatus validationStatus, boolean archived,
            boolean expectedResult
    ) {
        Certificate certificate = new Certificate();
        certificate.setState(certificateState);
        certificate.setValidationStatus(validationStatus);
        certificate.setArchived(archived);

        if (privateKeyAlgorithm != null || publicKeyAlgorithm != null) {
            CryptographicKey key = new CryptographicKey();
            Set<CryptographicKeyItem> items = new HashSet<>();
            if (privateKeyAlgorithm != null) {
                CryptographicKeyItem item = new CryptographicKeyItem();
                item.setType(privateKeyType);
                item.setKeyAlgorithm(privateKeyAlgorithm);
                item.setUsage(privateKeyUsages);
                item.setState(privateKeyState);
                item.setKey(key);
                items.add(item);
            }
            if (publicKeyAlgorithm != null) {
                CryptographicKeyItem item = new CryptographicKeyItem();
                item.setType(publicKeyType);
                item.setKeyAlgorithm(publicKeyAlgorithm);
                item.setUsage(publicKeyUsages);
                item.setState(KeyState.ACTIVE);
                item.setKey(key);
                items.add(item);
            }
            key.setItems(items);
            certificate.setKey(key);
        }

        Assertions.assertEquals(expectedResult, CertificateUtil.isCertificateCmpAcceptable(certificate), "Test case '" + testCaseName + "' failed");
    }

    @ParameterizedTest
    @MethodSource("com.czertainly.core.util.CertificateTestData#provideScepCaCertificateTestData")
    public void testIsCertificateScepCaCertAcceptable(
            String testCaseName,
            KeyType publicKeyType, KeyAlgorithm publicKeyAlgorithm, List<KeyUsage> publicKeyUsages,
            KeyType privateKeyType, KeyAlgorithm privateKeyAlgorithm, List<KeyUsage> privateKeyUsages, KeyState privateKeyState,
            CertificateState certificateState, CertificateValidationStatus validationStatus, boolean archived,
            boolean intuneEnabled, boolean expectedResult
    ) {
        Certificate certificate = new Certificate();
        certificate.setState(certificateState);
        certificate.setValidationStatus(validationStatus);
        certificate.setArchived(archived);

        if (privateKeyAlgorithm != null || publicKeyAlgorithm != null) {
            CryptographicKey key = new CryptographicKey();
            Set<CryptographicKeyItem> items = new HashSet<>();
            if (privateKeyAlgorithm != null) {
                CryptographicKeyItem item = new CryptographicKeyItem();
                item.setType(privateKeyType);
                item.setKeyAlgorithm(privateKeyAlgorithm);
                item.setUsage(privateKeyUsages);
                item.setState(privateKeyState);
                item.setKey(key);
                items.add(item);
            }
            if (publicKeyAlgorithm != null) {
                CryptographicKeyItem item = new CryptographicKeyItem();
                item.setType(publicKeyType);
                item.setKeyAlgorithm(publicKeyAlgorithm);
                item.setUsage(publicKeyUsages);
                item.setState(KeyState.ACTIVE);
                item.setKey(key);
                items.add(item);
            }
            key.setItems(items);
            certificate.setKey(key);
        }

        Assertions.assertEquals(expectedResult, CertificateUtil.isCertificateScepCaCertAcceptable(certificate, intuneEnabled), "Test case '" + testCaseName + "' failed");
    }
}
