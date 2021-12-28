package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.auth.EditAuthProfileDto;
import com.czertainly.api.model.core.auth.AuthProfileDto;
import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.AdminRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Test
    public void testGetAuthProfile() throws NotFoundException {
        Admin admin;
        Certificate certificate;
        CertificateContent certificateContent;

        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("123456789");
        certificate = certificateRepository.save(certificate);

        admin = new Admin();
        admin.setUsername("user");
        admin.setCertificate(certificate);
        admin.setSerialNumber(certificate.getSerialNumber());
        admin = adminRepository.save(admin);

        AuthProfileDto dto = authService.getAuthProfile();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(admin.getUsername(), dto.getUsername());
    }

    @Test
    public void testGetAuthProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> authService.getAuthProfile());
    }

    @Test
    public void testEditAuthProfile() throws NotFoundException {
        Admin admin;
        Certificate certificate;
        CertificateContent certificateContent;

        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("123456789");
        certificate = certificateRepository.save(certificate);

        admin = new Admin();
        admin.setUsername("user");
        admin.setName("Test1");
        admin.setSurname("Admin1");
        admin.setEmail("test@admin1.com");
        admin.setCertificate(certificate);
        admin.setSerialNumber(certificate.getSerialNumber());
        admin = adminRepository.save(admin);

        EditAuthProfileDto request = new EditAuthProfileDto();
        request.setName("Test2");
        request.setSurname("Admin2");
        request.setEmail("test@admin2.com");

        authService.editAuthProfile(request);

        AuthProfileDto dto = authService.getAuthProfile();
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(admin.getUsername(), dto.getUsername());
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertEquals(request.getSurname(), dto.getSurname());
        Assertions.assertEquals(request.getEmail(), dto.getEmail());
    }

    @Test
    public void testEditAuthProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> authService.editAuthProfile(new EditAuthProfileDto()));
    }
}
