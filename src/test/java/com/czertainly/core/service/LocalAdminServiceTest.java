package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.admin.AddAdminRequestDto;
import com.czertainly.api.model.core.admin.AdminDto;
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
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;

@SpringBootTest
@Transactional
@Rollback
public class LocalAdminServiceTest {

    private static final String ADMIN_NAME = "testAdmin1";

    @Autowired
    private LocalAdminService localAdminService;

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

        AdminDto dto = localAdminService.addAdmin(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getUsername(), dto.getUsername());
        Assertions.assertNotNull(dto.getCertificate());
        Assertions.assertEquals(request.getCertificateUuid() , dto.getCertificate().getUuid());
    }

    @Test
    public void testAddAdmin_validationFail() {
        AddAdminRequestDto request = new AddAdminRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> localAdminService.addAdmin(request));
    }

    @Test
    public void testAddAdmin_alreadyExist() {
        AddAdminRequestDto request = new AddAdminRequestDto();
        request.setUsername(ADMIN_NAME); // admin with same username exist
        request.setName("Test");
        request.setSurname("Admin1");
        request.setEmail("test@admin1.com");

        Assertions.assertThrows(AlreadyExistException.class, () -> localAdminService.addAdmin(request));
    }

    @Test
    public void testAddAdmin_alreadyExistBySerialNumber() {
        AddAdminRequestDto request = new AddAdminRequestDto();
        request.setUsername("testAdmin2");
        request.setName("Test");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");
        request.setCertificateUuid(certificate.getUuid()); // admin with same certificate exist

        Assertions.assertThrows(AlreadyExistException.class, () -> localAdminService.addAdmin(request));
    }
}