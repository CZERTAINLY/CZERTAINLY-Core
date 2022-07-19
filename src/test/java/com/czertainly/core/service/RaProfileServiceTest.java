package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.client.SimplifiedClientDto;
import com.czertainly.api.model.client.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ClientRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;
import java.util.List;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class RaProfileServiceTest {

    private static final String RA_PROFILE_NAME = "testRaProfile1";
    private static final String CLIENT_NAME = "testClient1";

    @Autowired
    private RaProfileService raProfileService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;

    private RaProfile raProfile;
    private Certificate certificate;
    private CertificateContent certificateContent;
    private Client client;
    private AuthorityInstanceReference authorityInstanceReference;
    private Connector connector;

    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());


        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("123456789");
        certificate = certificateRepository.save(certificate);

        client = new Client();
        client.setName(CLIENT_NAME);
        client.setCertificate(certificate);
        client.setSerialNumber(certificate.getSerialNumber());
        client = clientRepository.save(client);

        connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile = raProfileRepository.save(raProfile);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    public void testListRaProfiles() {
        List<RaProfileDto> raProfiles = raProfileService.listRaProfiles();
        Assertions.assertNotNull(raProfiles);
        Assertions.assertFalse(raProfiles.isEmpty());
        Assertions.assertEquals(1, raProfiles.size());
        Assertions.assertEquals(raProfile.getUuid(), raProfiles.get(0).getUuid());
    }

    @Test
    public void testGetRaProfileByUuid() throws NotFoundException {
        RaProfileDto dto = raProfileService.getRaProfile(raProfile.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(raProfile.getUuid(), dto.getUuid());
    }

    @Test
    public void testGetRaProfileByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.getRaProfile("wrong-uuid"));
    }

    @Test
    public void testAddRaProfile() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        AddRaProfileRequestDto request = new AddRaProfileRequestDto();
        request.setName("testRaProfile2");
        request.setAttributes(List.of());
        request.setAuthorityInstanceUuid(authorityInstanceReference.getUuid());

        RaProfileDto dto = raProfileService.addRaProfile(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddRaProfile_validationFail() {
        AddRaProfileRequestDto request = new AddRaProfileRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> raProfileService.addRaProfile(request));
    }

    @Test
    public void testAddRaProfile_alreadyExist() {
        AddRaProfileRequestDto request = new AddRaProfileRequestDto();
        request.setName(RA_PROFILE_NAME); // raProfile with same username exist

        Assertions.assertThrows(AlreadyExistException.class, () -> raProfileService.addRaProfile(request));
    }

    @Test
    public void testEditRaProfile() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/raProfile/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        EditRaProfileRequestDto request = new EditRaProfileRequestDto();
        request.setDescription("some description");
        request.setAttributes(List.of());
        request.setAuthorityInstanceUuid(authorityInstanceReference.getUuid());

        RaProfileDto dto = raProfileService.editRaProfile(raProfile.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditRaProfile_notFound() {
        EditRaProfileRequestDto request = new EditRaProfileRequestDto();

        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.editRaProfile("wrong-uuid", request));
    }

    @Test
    public void testRemoveRaProfile() throws NotFoundException {
        raProfileService.removeRaProfile(raProfile.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.getRaProfile(raProfile.getUuid()));
    }

    @Test
    public void testRemoveRaProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.removeRaProfile("wrong-uuid"));
    }

    @Test
    public void testEnableRaProfile() throws NotFoundException, CertificateException {
        raProfileService.enableRaProfile(raProfile.getUuid());
        Assertions.assertEquals(true, raProfile.getEnabled());
    }

    @Test
    public void testEnableRaProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.enableRaProfile("wrong-uuid"));
    }

    @Test
    public void testDisableRaProfile() throws NotFoundException {
        raProfileService.disableRaProfile(raProfile.getUuid());
        Assertions.assertEquals(false, raProfile.getEnabled());
    }

    @Test
    public void testDisableRaProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.disableRaProfile("wrong-uuid"));
    }

    @Test
    public void testListClients() throws NotFoundException {
        raProfile.getClients().add(client);
        raProfileRepository.save(raProfile);

        List<SimplifiedClientDto> clients = raProfileService.listClients(raProfile.getUuid());
        Assertions.assertNotNull(clients);
        Assertions.assertFalse(clients.isEmpty());
        Assertions.assertEquals(client.getUuid(), clients.get(0).getUuid());
    }

    @Test
    public void testListClients_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.listClients("wrong-uuid"));
    }

    @Test
    public void testListClients_emptyClients() throws NotFoundException {
        List<SimplifiedClientDto> auths = raProfileService.listClients(raProfile.getUuid());
        Assertions.assertNotNull(auths);
        Assertions.assertTrue(auths.isEmpty());
    }

    @Test
    public void testBulkRemove() {
        raProfileService.bulkRemoveRaProfile(List.of(raProfile.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> raProfileService.getRaProfile(raProfile.getUuid()));
    }

    @Test
    public void testBulkEnable() {
        raProfileService.bulkEnableRaProfile(List.of(raProfile.getUuid()));
        Assertions.assertTrue(raProfile.getEnabled());
    }

    @Test
    public void testBulkDisable() {
        raProfileService.bulkDisableRaProfile(List.of(raProfile.getUuid()));
        Assertions.assertFalse(raProfile.getEnabled());
    }
}
