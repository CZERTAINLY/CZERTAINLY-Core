package com.czertainly.core.service;

import com.czertainly.api.core.modal.AddAdminRequestDto;
import com.czertainly.api.core.modal.AdminDto;
import com.czertainly.api.core.modal.EditAdminRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.AdminRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
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
public class AdminServiceTest {

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
        admin.setSerialNumber(certificate.getSerialNumber());
        admin = adminRepository.save(admin);
    }

    @Test
    public void testListAdmins() {
        List<AdminDto> admins = adminService.listAdmins();
        Assertions.assertNotNull(admins);
        Assertions.assertFalse(admins.isEmpty());
        Assertions.assertEquals(1, admins.size());
        Assertions.assertEquals(admin.getUuid(), admins.get(0).getUuid());
    }

    @Test
    public void testGetAdminByUuid() throws NotFoundException {
        AdminDto dto = adminService.getAdminByUuid(admin.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(admin.getUuid(), dto.getUuid());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(admin.getCertificate().getUuid(), dto.getCertificate().getUuid());
    }

    @Test
    public void testGetAdminByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.getAdminByUuid("wrong-uuid"));
    }

    @Test
    public void testAddAdmin() throws NotFoundException, CertificateException, AlreadyExistException {
        Certificate admin2Cert = new Certificate();
        admin2Cert.setCertificateContent(certificateContent);
        admin2Cert.setSerialNumber("987654321");
        admin2Cert = certificateRepository.save(admin2Cert);

        AddAdminRequestDto request = new AddAdminRequestDto();
        request.setUsername("testAdmin2");
        request.setName("Test");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");
        request.setCertificateUuid(admin2Cert.getUuid());

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
        request.setCertificateUuid(certificate.getUuid()); // admin with same certificate exist

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
        request.setCertificateUuid(admin2Cert.getUuid());

        AdminDto dto = adminService.editAdmin(admin.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getSurname(), dto.getSurname());
        Assertions.assertEquals(request.getEmail(), dto.getEmail());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(request.getCertificateUuid(), dto.getCertificate().getUuid());
    }

    @Test
    public void testEditAdmin_validationFail() {
        EditAdminRequestDto request = new EditAdminRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> adminService.editAdmin(admin.getUuid(), request));
    }

    @Test
    public void testEditAdmin_notFound() {
        EditAdminRequestDto request = new EditAdminRequestDto();
        request.setName("Test");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");

        Assertions.assertThrows(NotFoundException.class, () -> adminService.editAdmin("wrong-uuid", request));
    }

    @Test
    public void testRemoveAdmin() throws NotFoundException {
        adminService.removeAdmin(admin.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> adminService.getAdminByUuid(admin.getUuid()));
    }

    @Test
    public void testRemoveAdmin_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.removeAdmin("wrong-uuid"));
    }

    @Test
    public void testEnableAdmin() throws NotFoundException, CertificateException {
        adminService.enableAdmin(admin.getUuid());
        Assertions.assertEquals(true, admin.getEnabled());
    }

    @Test
    public void testEnableAdmin_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.enableAdmin("wrong-uuid"));
    }

    @Test
    public void testDisableAdmin() throws NotFoundException {
        adminService.disableAdmin(admin.getUuid());
        Assertions.assertEquals(false, admin.getEnabled());
    }

    @Test
    public void testDisableAdmin_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> adminService.disableAdmin("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() {
        adminService.bulkRemoveAdmin(List.of(admin.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> adminService.getAdminByUuid(admin.getUuid()));
    }

    @Test
    public void testBulkEnable() {
        adminService.bulkEnableAdmin(List.of(admin.getUuid()));
        Assertions.assertTrue(admin.getEnabled());
    }

    @Test
    public void testBulkDisable() {
        adminService.bulkDisableAdmin(List.of(admin.getUuid()));
        Assertions.assertFalse(admin.getEnabled());
    }
}
