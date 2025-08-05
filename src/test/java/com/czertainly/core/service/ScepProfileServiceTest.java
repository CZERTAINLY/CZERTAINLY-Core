package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.scep.ScepProfileEditRequestDto;
import com.czertainly.api.model.client.scep.ScepProfileRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyFormat;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.scep.ScepProfileDetailDto;
import com.czertainly.api.model.core.scep.ScepProfileDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.scep.ScepProfile;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.scep.ScepProfileRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ScepProfileServiceTest extends BaseSpringBootTest {

    @Autowired
    private ScepProfileService scepProfileService;

    @Autowired
    private ScepProfileRepository scepProfileRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;
    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private TokenProfileRepository tokenProfileRepository;
    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    private TokenInstanceReference tokenInstanceReference;
    private CryptographicKeyItem content;
    private CryptographicKeyItem content1;
    private TokenProfile tokenProfile;
    private Connector connector;
    private CryptographicKey key;

    private ScepProfile scepProfile;
    private CertificateContent certificateContent;
    private Certificate certificate;

    @BeforeEach
    void setUp() {

        connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReferenceRepository.save(tokenInstanceReference);

        tokenProfile = new TokenProfile();
        tokenProfile.setName("profile1");
        tokenProfile.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile.setDescription("sample description");
        tokenProfile.setEnabled(true);
        tokenProfile.setTokenInstanceName("testInstance");
        tokenProfileRepository.save(tokenProfile);

        key = new CryptographicKey();
        key.setName("testKey1");
        key.setTokenProfile(tokenProfile);
        key.setTokenInstanceReference(tokenInstanceReference);
        key.setDescription("initial description");
        cryptographicKeyRepository.save(key);

        content = new CryptographicKeyItem();
        content.setLength(1024);
        content.setKey(key);
        content.setKeyUuid(key.getUuid());
        content.setType(KeyType.PRIVATE_KEY);
        content.setKeyData("some/encrypted/data");
        content.setFormat(KeyFormat.PRKI);
        content.setState(KeyState.ACTIVE);
        content.setEnabled(true);
        content.setKeyAlgorithm(KeyAlgorithm.RSA);
        content.setUsage(List.of(KeyUsage.DECRYPT, KeyUsage.SIGN));
        cryptographicKeyItemRepository.save(content);

        content1 = new CryptographicKeyItem();
        content1.setLength(1024);
        content1.setKey(key);
        content1.setKeyUuid(key.getUuid());
        content1.setType(KeyType.PUBLIC_KEY);
        content1.setKeyData("some/encrypted/data");
        content1.setFormat(KeyFormat.SPKI);
        content1.setState(KeyState.ACTIVE);
        content1.setEnabled(true);
        content1.setKeyAlgorithm(KeyAlgorithm.RSA);
        content1.setUsage(List.of(KeyUsage.ENCRYPT, KeyUsage.VERIFY));
        cryptographicKeyItemRepository.save(content1);

        content.setKeyReferenceUuid(content.getUuid());
        content1.setKeyReferenceUuid(content1.getUuid());
        cryptographicKeyItemRepository.save(content);
        cryptographicKeyItemRepository.save(content1);

        Set<CryptographicKeyItem> items = new HashSet<>();
        items.add(content1);
        items.add(content);
        key.setItems(items);
        cryptographicKeyRepository.save(key);

        certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setKey(key);
        certificate = certificateRepository.save(certificate);

        scepProfile = new ScepProfile();
        scepProfile.setDescription("sample description");
        scepProfile.setName("sameName");
        scepProfile.setRequireManualApproval(false);
        scepProfile.setChallengePassword("test123");
        scepProfile.setIncludeCaCertificate(true);
        scepProfile.setEnabled(false);
        scepProfile.setCaCertificate(certificate);
        scepProfileRepository.save(scepProfile);
    }

    @Test
    void testListScepProfiles() {
        scepProfile.setEnabled(true);
        scepProfileRepository.save(scepProfile);
        List<ScepProfileDto> scepProfiles = scepProfileService.listScepProfile(SecurityFilter.create());
        Assertions.assertNotNull(scepProfiles);
        Assertions.assertFalse(scepProfiles.isEmpty());
        Assertions.assertEquals(1, scepProfiles.size());
        Assertions.assertEquals(scepProfile.getUuid().toString(), scepProfiles.get(0).getUuid());
    }

    @Test
    void testGetScepProfileByUuid() throws NotFoundException {
        scepProfile.setEnabled(true);
        scepProfileRepository.save(scepProfile);
        ScepProfileDetailDto dto = scepProfileService.getScepProfile(scepProfile.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(scepProfile.getUuid().toString(), dto.getUuid());
    }

    @Test
    void testGetScepProfileByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> scepProfileService.getScepProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testAddScepProfile() throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException {

        ScepProfileRequestDto request = new ScepProfileRequestDto();
        request.setName("Test");
        request.setDescription("sample");
        request.setChallengePassword("1234");
        request.setCaCertificateUuid(certificate.getUuid().toString());
        certificate.setArchived(true);
        certificateRepository.save(certificate);
        Assertions.assertThrows(ValidationException.class, () -> scepProfileService.createScepProfile(request));
        certificate.setArchived(false);
        certificate.setState(CertificateState.FAILED);
        certificateRepository.save(certificate);
        Assertions.assertThrows(ValidationException.class, () -> scepProfileService.createScepProfile(request));

        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.EXPIRED);
        certificateRepository.save(certificate);
        Assertions.assertThrows(ValidationException.class, () -> scepProfileService.createScepProfile(request));

        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificateRepository.save(certificate);


        ScepProfileDetailDto dto = scepProfileService.createScepProfile(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    void testAddScepProfile_validationFail() {
        ScepProfileRequestDto request = new ScepProfileRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> scepProfileService.createScepProfile(request));
    }

    @Test
    void testAddScepProfile_alreadyExist() {
        ScepProfileRequestDto request = new ScepProfileRequestDto();
        request.setName("sameName");

        Assertions.assertThrows(AlreadyExistException.class, () -> scepProfileService.createScepProfile(request));
    }

    @Test
    void testEditScepProfile() throws ConnectorException, AttributeException, NotFoundException {
        scepProfile.setEnabled(false);
        scepProfileRepository.save(scepProfile);

        ScepProfileEditRequestDto request = new ScepProfileEditRequestDto();
        request.setDescription("sample11");
        request.setCaCertificateUuid(certificate.getUuid().toString());

        ScepProfileDetailDto dto = scepProfileService.editScepProfile(scepProfile.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    void testEditScepProfile_validationFail() {
        ScepProfileEditRequestDto request = new ScepProfileEditRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> scepProfileService.editScepProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    void testRemoveScepProfile() throws NotFoundException {
        scepProfileService.deleteScepProfile(scepProfile.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> scepProfileService.getScepProfile(scepProfile.getSecuredUuid()));
    }

    @Test
    void testRemoveScepProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> scepProfileService.getScepProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testEnableScepProfile() throws NotFoundException {
        scepProfileService.enableScepProfile(scepProfile.getSecuredUuid());
        Assertions.assertTrue(scepProfileService.getScepProfile(scepProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testEnableScepProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> scepProfileService.enableScepProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testDisableScepProfile() throws NotFoundException {
        scepProfile.setEnabled(true);
        scepProfileRepository.save(scepProfile);
        scepProfileService.disableScepProfile(scepProfile.getSecuredUuid());
        Assertions.assertFalse(scepProfileService.getScepProfile(scepProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testDisableScepProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> scepProfileService.disableScepProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testBulkRemove() {
        scepProfileService.bulkDeleteScepProfile(List.of(scepProfile.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> scepProfileService.getScepProfile(scepProfile.getSecuredUuid()));
    }

    @Test
    void testBulkEnable() throws NotFoundException {
        scepProfile.setEnabled(true);
        scepProfileRepository.save(scepProfile);
        scepProfileService.bulkEnableScepProfile(List.of(scepProfile.getSecuredUuid()));
        Assertions.assertTrue(scepProfileService.getScepProfile(scepProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testBulkDisable() throws NotFoundException {
        scepProfileService.bulkDisableScepProfile(List.of(scepProfile.getSecuredUuid()));
        Assertions.assertFalse(scepProfileService.getScepProfile(scepProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = scepProfileService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }
}
