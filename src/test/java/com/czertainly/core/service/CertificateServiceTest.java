package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class CertificateServiceTest extends BaseSpringBootTest {

    @Autowired
    private CertificateService certificateService;
    @Autowired
    private MetadataService metadataService;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;
    @Autowired
    private GroupRepository groupRepository;

    private Certificate certificate;
    private Certificate adminCertificate;
    private CertificateContent certificateContent;
    private RaProfile raProfile;
    private RaProfile raProfileOld;
    private Group group;

    private X509Certificate x509Cert;

    private AuthorityInstanceReference authorityInstance;
    private Connector connector;
    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("authorityInstanceConnector");
        connector.setUrl("http://localhost:"+mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.AUTHORITY_PROVIDER);
        functionGroup.setName(FunctionGroupCode.AUTHORITY_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setConnectorUuid(connector.getUuid());
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setFunctionGroupUuid(functionGroup.getUuid());
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("ApiKey")));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        authorityInstance = new AuthorityInstanceReference();
        authorityInstance.setName("testAuthorityInstance1");
        authorityInstance.setConnector(connector);
        authorityInstance.setConnectorUuid(connector.getUuid());
        authorityInstance.setKind("sample");
        authorityInstance.setAuthorityInstanceUuid("1l");
        authorityInstance = authorityInstanceReferenceRepository.save(authorityInstance);

        certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        raProfileOld = new RaProfile();
        raProfileOld.setName("Test RA profile Old");
        raProfileOld.setAuthorityInstanceReference(authorityInstance);
        raProfileOld = raProfileRepository.save(raProfileOld);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCercertificatetificate");
        certificate.setSerialNumber("123456789");
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate.setRaProfile(raProfileOld);
        certificate = certificateRepository.save(certificate);

        List<MetadataAttribute> meta = new ArrayList<>();
        MetadataAttribute tst = new MetadataAttribute();
        tst.setType(AttributeType.META);
        tst.setName("Test");
        tst.setUuid("9f94036e-f050-4c9c-a3b8-f47b1be696aa");
        tst.setProperties(new MetadataAttributeProperties());
        tst.setContent(List.of(new StringAttributeContent("xyz", "xyz")));
        meta.add(tst);

        UUID connectorUuid = raProfileOld.getAuthorityInstanceReference().getConnectorUuid();
        metadataService.createMetadataDefinitions(connectorUuid, meta);
        metadataService.createMetadata(connectorUuid, certificate.getUuid(), null, null, meta, Resource.CERTIFICATE, null);

        raProfile = new RaProfile();
        raProfile.setName("Test RA profile");
        raProfile.setAuthorityInstanceReference(authorityInstance);
        raProfile = raProfileRepository.save(raProfile);

        group = new Group();
        group = groupRepository.save(group);

        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyStoreStream, "123456".toCharArray());

        x509Cert = (X509Certificate) keyStore.getCertificate("1");
    }

    @Test
    public void testListCertificates() {
        CertificateResponseDto certificateEntities = certificateService.listCertificates(SecurityFilter.create(), new SearchRequestDto());
        Assertions.assertNotNull(certificateEntities);
        Assertions.assertFalse(certificateEntities.getCertificates().isEmpty());
        Assertions.assertEquals(1, certificateEntities.getCertificates().size());
        Assertions.assertEquals(certificate.getUuid().toString(), certificateEntities.getCertificates().get(0).getUuid());
    }

    @Test
    public void testGetCertificate() throws NotFoundException, CertificateException, IOException {
        CertificateDetailDto dto = certificateService.getCertificate(certificate.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificate.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(certificate.getSerialNumber(), dto.getSerialNumber());
    }

    @Test
    public void testGetCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.getCertificate(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testCreateCertificateEntity() {
        Certificate cert = certificateService.createCertificateEntity(x509Cert);

        Assertions.assertNotNull(cert);
        Assertions.assertEquals("CLIENT1", cert.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", cert.getSerialNumber());
    }

    @Test
    public void testCheckCreateCertificate() throws CertificateException, AlreadyExistException, NoSuchAlgorithmException {
        Certificate cert = certificateService.checkCreateCertificate(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));

        Assertions.assertNotNull(cert);
        Assertions.assertEquals("CLIENT1", cert.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", cert.getSerialNumber());
    }

    @Test
    public void testAddCertificate_certificateException() {
        Assertions.assertThrows(CertificateException.class, () -> certificateService.checkCreateCertificate("certificate"));
    }

    @Test
    public void testRemoveCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.deleteCertificate(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testRevokeCertificate() throws NotFoundException, CertificateException, IOException {
        certificateService.revokeCertificate(certificate.getSerialNumber());

        CertificateDetailDto dto = certificateService.getCertificate(certificate.getSecuredUuid());

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificate.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(certificate.getSerialNumber(), dto.getSerialNumber());
        Assertions.assertEquals(CertificateState.REVOKED, dto.getState());
    }

    @Test
    @Disabled("Revoke doesn't throw NotFoundException")
    public void testRevokeCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.revokeCertificate("abfbc322-29e1-11ed-a261-0242ac120002"));
    }

    @Test
    public void testUploadCertificate() throws CertificateException, AlreadyExistException, NoSuchAlgorithmException {
        UploadCertificateRequestDto request = new UploadCertificateRequestDto();
        request.setCertificate(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));

        CertificateDetailDto dto = certificateService.upload(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("CLIENT1", dto.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", dto.getSerialNumber());
    }

    @Test
    public void testUpdateRaProfile() throws NotFoundException, CertificateOperationException, CertificateException, IOException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/identify"))
                .willReturn(WireMock.okJson("{\"meta\":[{\"uuid\":\"b42ab690-60fd-11ed-9b6a-0242ac120002\",\"name\":\"ejbcaUsername\",\"description\":\"EJBCA Username\",\"content\":[{\"reference\":\"ShO0lp7qbnE=\",\"data\":\"ShO0lp7qbnE=\"}],\"type\":\"meta\",\"contentType\":\"string\",\"properties\":{\"label\":\"EJBCA Username\",\"visible\":true,\"group\":null,\"global\":false}}]}")));

        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setRaProfileUuid(raProfile.getUuid().toString());
        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto);

        Assertions.assertEquals(raProfile, certificate.getRaProfile());
        CertificateDetailDto certificateDetailDto = certificateService.getCertificate(certificate.getSecuredUuid());

        Assertions.assertEquals(1, certificateDetailDto.getMetadata().size());
        Assertions.assertEquals(connector.getName(), certificateDetailDto.getMetadata().get(0).getConnectorName());
        Assertions.assertEquals(1, certificateDetailDto.getMetadata().get(0).getItems().size());
    }

    @Test
    public void testUpdateRaProfileFails() throws NotFoundException, CertificateOperationException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/identify"))
                .willReturn(WireMock.jsonResponse("{\"message\": \"Object of type 'Certificate' not identified.\"}", 404)));

        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setRaProfileUuid(raProfile.getUuid().toString());

        Assertions.assertThrows(CertificateOperationException.class, () -> certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto));

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/identify"))
                .willReturn(WireMock.jsonResponse("[\"Object of type 'Certificate' identified but not valid according RA profile attributes.\"]", 422)));

        Assertions.assertThrows(CertificateOperationException.class, () -> certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto));
    }

    @Test
    public void testUpdateRaProfile_certificateNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setRaProfileUuid(raProfile.getUuid().toString());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), uuidDto));
    }

    @Test
    public void testUpdateRaProfile_raProfileNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setRaProfileUuid("abfbc322-29e1-11ed-a261-0242ac120002");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto));
    }

    @Test
    public void testUpdateCertificateGroup() throws NotFoundException, CertificateOperationException {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setGroupUuid(group.getUuid().toString());

        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto);

        Assertions.assertEquals(group, certificate.getGroup());
    }

    @Test
    public void testUpdateCertificateGroup_certificateNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setGroupUuid(group.getUuid().toString());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), uuidDto));
    }

    @Test
    public void testUpdateCertificateGroup_groupNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setGroupUuid("abfbc322-29e1-11ed-a261-0242ac120002");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto));
    }


    @Test
    @Disabled("get user from API")
    public void testUpdateOwner() throws NotFoundException, CertificateOperationException {
        CertificateUpdateObjectsDto request = new CertificateUpdateObjectsDto();
        request.setOwnerUuid("newOwner");
        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), request);

        Assertions.assertEquals(request.getOwnerUuid(), certificate.getOwner());
    }

    @Test
    @Disabled("get user from API")
    public void testUpdateCertificateOwner_certificateNotFound() {
        CertificateUpdateObjectsDto dto = new CertificateUpdateObjectsDto();
        dto.setOwnerUuid("testOwner");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), dto));
    }

    //TODO lukas.rejha need to be fixed
    @Disabled
    @Test
    public void testSearchableFields() {
        final List<SearchFieldDataByGroupDto> response = certificateService.getSearchableFieldInformationByGroup();
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.isEmpty());
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        RemoveCertificateDto request = new RemoveCertificateDto();
        request.setUuids(List.of(certificate.getUuid().toString()));

        certificateService.bulkDeleteCertificate(SecurityFilter.create(), request);

        Assertions.assertThrows(NotFoundException.class, () -> certificateService.getCertificate(certificate.getSecuredUuid()));
    }
}
