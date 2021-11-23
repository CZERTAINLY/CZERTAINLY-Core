package com.czertainly.core.service;

import com.czertainly.api.core.modal.AddClientRequestDto;
import com.czertainly.api.core.modal.ClientDto;
import com.czertainly.api.core.modal.EditClientRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.ClientRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class ClientServiceTest {

    private static final String CLIENT_NAME = "testClient1";

    @Autowired
    private ClientService clientService;

    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private EntityManager entityManager;

    private Client client;
    private Certificate certificate;
    private CertificateContent certificateContent;
    private RaProfile raProfile;

    @BeforeEach
    public void setUp() {
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

        raProfile = new RaProfile();
        raProfile.setName("testRAProfile1");
        raProfile = raProfileRepository.save(raProfile);
    }

    @Test
    public void testListClients() {
        List<ClientDto> clients = clientService.listClients();
        Assertions.assertNotNull(clients);
        Assertions.assertFalse(clients.isEmpty());
        Assertions.assertEquals(1, clients.size());
        Assertions.assertEquals(client.getUuid(), clients.get(0).getUuid());
    }

    @Test
    public void testGetClientByUuid() throws NotFoundException {
        ClientDto dto = clientService.getClient(client.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(client.getUuid(), dto.getUuid());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(client.getCertificate().getUuid(), dto.getCertificate().getUuid());
    }

    @Test
    public void testGetClientByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.getClient("wrong-uuid"));
    }

    @Test
    public void testAddClient() throws NotFoundException, CertificateException, AlreadyExistException {
        Certificate client2Cert = new Certificate();
        client2Cert.setCertificateContent(certificateContent);
        client2Cert.setSerialNumber("987654321");
        client2Cert = certificateRepository.save(client2Cert);

        AddClientRequestDto request = new AddClientRequestDto();
        request.setName("testClient2");
        request.setCertificateUuid(client2Cert.getUuid());

        ClientDto dto = clientService.addClient(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(request.getCertificateUuid() , dto.getCertificate().getUuid());
    }

    @Test
    public void testAddClient_validationFail() {
        AddClientRequestDto request = new AddClientRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> clientService.addClient(request));
    }

    @Test
    public void testAddClient_alreadyExist() {
        AddClientRequestDto request = new AddClientRequestDto();
        request.setName(CLIENT_NAME); // client with same username exist
        request.setCertificateUuid(certificate.getUuid());

        Assertions.assertThrows(AlreadyExistException.class, () -> clientService.addClient(request));
    }

    @Test
    public void testAddClient_alreadyExistBySerialNumber() {
        AddClientRequestDto request = new AddClientRequestDto();
        request.setName("testClient2");
        request.setCertificateUuid(certificate.getUuid()); // client with same certificate exist

        Assertions.assertThrows(AlreadyExistException.class, () -> clientService.addClient(request));
    }

    @Test
    public void testEditClient() throws NotFoundException, CertificateException, AlreadyExistException {
        Certificate client2Cert = new Certificate();
        client2Cert.setCertificateContent(certificateContent);
        client2Cert.setSerialNumber("987654321");
        client2Cert = certificateRepository.save(client2Cert);

        EditClientRequestDto request = new EditClientRequestDto();
        request.setDescription("some description");
        request.setCertificateUuid(client2Cert.getUuid());

        ClientDto dto = clientService.editClient(client.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(request.getCertificateUuid(), dto.getCertificate().getUuid());
    }

    @Test
    public void testEditClient_notFound() {
        EditClientRequestDto request = new EditClientRequestDto();

        Assertions.assertThrows(NotFoundException.class, () -> clientService.editClient("wrong-uuid", request));
    }

    @Test
    public void testRemoveClient() throws NotFoundException {
        clientService.removeClient(client.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> clientService.getClient(client.getUuid()));
    }

    @Test
    public void testRemoveClient_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.removeClient("wrong-uuid"));
    }

    @Test
    public void testEnableClient() throws NotFoundException, CertificateException {
        clientService.enableClient(client.getUuid());
        Assertions.assertEquals(true, client.getEnabled());
    }

    @Test
    public void testEnableClient_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.enableClient("wrong-uuid"));
    }

    @Test
    public void testDisableClient() throws NotFoundException {
        clientService.disableClient(client.getUuid());
        Assertions.assertEquals(false, client.getEnabled());
    }

    @Test
    public void testDisableClient_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.disableClient("wrong-uuid"));
    }

    @Test
    public void testListAuthorizations() throws NotFoundException {
        client.getRaProfiles().add(raProfile);
        clientRepository.save(client);

        List<RaProfileDto> auths = clientService.listAuthorizations(client.getUuid());
        Assertions.assertNotNull(auths);
        Assertions.assertFalse(auths.isEmpty());
        Assertions.assertEquals(raProfile.getUuid(), auths.get(0).getUuid());
    }

    @Test
    public void testListAuthorizations_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.listAuthorizations("wrong-uuid"));
    }

    @Test
    public void testListAuthorizations_emptyAuthorizations() throws NotFoundException {
        List<RaProfileDto> auths = clientService.listAuthorizations(client.getUuid());
        Assertions.assertNotNull(auths);
        Assertions.assertTrue(auths.isEmpty());
    }

    @Test
    public void testAuthorizeClient() throws NotFoundException {
        clientService.authorizeClient(client.getUuid(), raProfile.getUuid());

        // refresh relations
        entityManager.flush();
        entityManager.refresh(client);

        List<RaProfileDto> auths = clientService.listAuthorizations(client.getUuid());
        Assertions.assertNotNull(auths);
        Assertions.assertFalse(auths.isEmpty());
        Assertions.assertEquals(raProfile.getUuid(), auths.get(0).getUuid());
    }

    @Test
    public void testAuthorizeClient_clientNotFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.authorizeClient("wrong-uuid", raProfile.getUuid()));
    }

    @Test
    public void testAuthorizeClient_raProfileNotFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.authorizeClient(client.getUuid(), "wrong-uuid"));
    }

    @Test
    public void testUnauthorizeClient() throws NotFoundException {
        client.getRaProfiles().add(raProfile);
        clientRepository.save(client);

        clientService.unauthorizeClient(client.getUuid(), raProfile.getUuid());

        // refresh relations
        entityManager.flush();
        entityManager.refresh(client);

        List<RaProfileDto> auths = clientService.listAuthorizations(client.getUuid());
        Assertions.assertNotNull(auths);
        Assertions.assertTrue(auths.isEmpty());
    }

    @Test
    public void testUnauthorizeClient_clientNotFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.unauthorizeClient("wrong-uuid", raProfile.getUuid()));
    }

    @Test
    public void testUnauthorizeClient_raProfileNotFound() {
        Assertions.assertThrows(NotFoundException.class, () -> clientService.unauthorizeClient(client.getUuid(), "wrong-uuid"));
    }

    @Test
    public void testBulkRemove() {
        clientService.bulkRemoveClient(List.of(client.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> clientService.getClient(client.getUuid()));
    }

    @Test
    public void testBulkEnable() {
        clientService.bulkEnableClient(List.of(client.getUuid()));
        Assertions.assertTrue(client.getEnabled());
    }

    @Test
    public void testBulkDisable() {
        clientService.bulkDisableClient(List.of(client.getUuid()));
        Assertions.assertFalse(client.getEnabled());
    }
}
