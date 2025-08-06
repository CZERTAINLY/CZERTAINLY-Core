package com.czertainly.core.service;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserManagementServiceTest extends BaseSpringBootTest {

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private UserManagementService userManagementService;

    @Test
    void testDoNotUseArchivedCertificates() {
        Certificate archivedCertificate = new Certificate();
        archivedCertificate.setArchived(true);
        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("content");
        certificateContentRepository.save(certificateContent);
        archivedCertificate.setCertificateContent(certificateContent);
        certificateRepository.save(archivedCertificate);

        AddUserRequestDto addUserRequestDto = new AddUserRequestDto();
        addUserRequestDto.setCertificateUuid(archivedCertificate.getUuid().toString());
        addUserRequestDto.setUsername("username");

        Assertions.assertThrows(ValidationException.class, () -> userManagementService.createUser(addUserRequestDto));
    }
}
