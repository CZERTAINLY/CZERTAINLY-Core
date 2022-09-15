package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.client.admin.EditAdminRequestDto;
import com.czertainly.api.model.core.admin.AdminDto;
import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.AdminRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.cert.CertificateException;
import java.util.List;

public class AdminServiceTest extends BaseSpringBootTest {

    private static final String ADMIN_NAME = "testAdmin1";

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    private Admin admin;
    private Certificate certificate;
    private CertificateContent certificateContent;

    @BeforeEach
    public void setUp() {
        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("123456789");
        certificate = certificateRepository.save(certificate);

        admin = new Admin();
        admin.setUsername(ADMIN_NAME);
        admin.setCertificate(certificate);
        admin.setCertificateUuid(certificate.getUuid());
        admin.setSerialNumber(certificate.getSerialNumber());
        admin = adminRepository.save(admin);
    }

    @Test
    public void testListAdmins() {
        List<AdminDto> admins = adminService.listAdmins(SecurityFilter.create());
        Assertions.assertNotNull(admins);
        Assertions.assertFalse(admins.isEmpty());
        Assertions.assertEquals(1, admins.size());
        Assertions.assertEquals(admin.getUuid().toString(), admins.get(0).getUuid());
    }

    @Test
    public void testGetAdminByUuid() throws NotFoundException {
        AdminDto dto = adminService.getAdminByUuid(admin.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(admin.getUuid().toString(), dto.getUuid());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(admin.getCertificateUuid().toString(), dto.getCertificate().getUuid());
    }

    @Test
    public void testGetAdminByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.getAdminByUuid(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testAddAdmin() throws NotFoundException, CertificateException, AlreadyExistException {
        Certificate admin2Cert = new Certificate();
        admin2Cert.setCertificateContent(certificateContent);
        admin2Cert.setCertificateContentId(certificateContent.getId());
        admin2Cert.setSerialNumber("987654321");
        admin2Cert = certificateRepository.save(admin2Cert);

        AddAdminRequestDto request = new AddAdminRequestDto();
        request.setUsername("testAdmin2");
        request.setName("Test");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");
        request.setCertificateUuid(admin2Cert.getUuid().toString());

        AdminDto dto = adminService.addAdmin(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getUsername(), dto.getUsername());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(request.getCertificateUuid() , dto.getCertificate().getUuid());
    }

    @Test
    public void testAddAdmin_validationFail() {
        AddAdminRequestDto request = new AddAdminRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> adminService.addAdmin(request));
    }

    @Test
    public void testAddAdmin_alreadyExist() {
        AddAdminRequestDto request = new AddAdminRequestDto();
        request.setUsername(ADMIN_NAME); // admin with same username exist
        request.setName("Test");
        request.setSurname("Admin1");
        request.setEmail("test@admin1.com");

        Assertions.assertThrows(AlreadyExistException.class, () -> adminService.addAdmin(request));
    }

    @Test
    public void testAddAdmin_alreadyExistBySerialNumber() {
        AddAdminRequestDto request = new AddAdminRequestDto();
        request.setUsername("testAdmin2");
        request.setName("Test");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");
        request.setCertificateUuid(certificate.getUuid().toString()); // admin with same certificate exist

        Assertions.assertThrows(AlreadyExistException.class, () -> adminService.addAdmin(request));
    }

    @Test
    public void testEditAdmin() throws NotFoundException, CertificateException, AlreadyExistException {
        Certificate admin2Cert = new Certificate();
        admin2Cert.setCertificateContent(certificateContent);
        admin2Cert.setSerialNumber("987654321");
        admin2Cert = certificateRepository.save(admin2Cert);

        EditAdminRequestDto request = new EditAdminRequestDto();
        request.setName("Test");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");
        request.setCertificateUuid(admin2Cert.getUuid().toString());

        AdminDto dto = adminService.editAdmin(admin.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getSurname(), dto.getSurname());
        Assertions.assertEquals(request.getEmail(), dto.getEmail());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(request.getCertificateUuid(), dto.getCertificate().getUuid());
    }

    @Test
    public void testEditAdmin_validationFail() {
        EditAdminRequestDto request = new EditAdminRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> adminService.editAdmin(admin.getSecuredUuid(), request));
    }

    @Test
    public void testEditAdmin_notFound() {
        EditAdminRequestDto request = new EditAdminRequestDto();
        request.setName("Test");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");

        Assertions.assertThrows(NotFoundException.class, () -> adminService.editAdmin(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    public void testRemoveAdmin() throws NotFoundException {
        adminService.deleteAdmin(admin.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> adminService.getAdminByUuid(admin.getSecuredUuid()));
    }

    @Test
    public void testRemoveAdmin_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.deleteAdmin(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testEnableAdmin() throws NotFoundException, CertificateException {
        adminService.enableAdmin(admin.getSecuredUuid());
        Assertions.assertEquals(true, admin.getEnabled());
    }

    @Test
    public void testEnableAdmin_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.enableAdmin(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testDisableAdmin() throws NotFoundException {
        adminService.disableAdmin(admin.getSecuredUuid());
        Assertions.assertEquals(false, admin.getEnabled());
    }

    @Test
    public void testDisableAdmin_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.disableAdmin(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testBulkRemove() {
        adminService.bulkDeleteAdmin(List.of(admin.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> adminService.getAdminByUuid(admin.getSecuredUuid()));
    }

    @Test
    public void testBulkEnable() {
        adminService.bulkEnableAdmin(List.of(admin.getSecuredUuid()));
        Assertions.assertTrue(admin.getEnabled());
    }

    @Test
    public void testBulkDisable() {
        adminService.bulkDisableAdmin(List.of(admin.getSecuredUuid()));
        Assertions.assertFalse(admin.getEnabled());
    }
}
