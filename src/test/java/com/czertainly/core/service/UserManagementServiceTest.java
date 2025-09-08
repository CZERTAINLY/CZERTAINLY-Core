package com.czertainly.core.service;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.util.BaseSpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

class UserManagementServiceTest extends BaseSpringBootTest {

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private FindByIndexNameSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    UserManagementApiClient userManagementApiClient;

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

    @Test
    void removeDisabledAndDeletedUserSession() {
        setupSessionTables();
        UUID userUuid = UUID.randomUUID();
        createSession(userUuid);
        Assertions.assertFalse(sessionRepository.findByPrincipalName(userUuid.toString()).isEmpty());
        userManagementService.deleteUser(userUuid.toString());
        Assertions.assertTrue(sessionRepository.findByPrincipalName(userUuid.toString()).isEmpty());

        createSession(userUuid);
        userManagementService.disableUser(userUuid.toString());
        Assertions.assertTrue(sessionRepository.findByPrincipalName(userUuid.toString()).isEmpty());
    }

    private void createSession(UUID userUuid) {
        Session s = sessionRepository.createSession();
        s.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                userUuid.toString()
        );
        sessionRepository.save(s);
    }

    void setupSessionTables() {
        // Create spring_session table
        jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS spring_session (
                        PRIMARY_ID CHAR(36) NOT NULL,
                        SESSION_ID CHAR(36) NOT NULL,
                        CREATION_TIME BIGINT NOT NULL,
                        LAST_ACCESS_TIME BIGINT NOT NULL,
                        MAX_INACTIVE_INTERVAL INT NOT NULL,
                        EXPIRY_TIME BIGINT NOT NULL,
                        PRINCIPAL_NAME VARCHAR(100),
                        CONSTRAINT spring_session_pkey PRIMARY KEY(PRIMARY_ID)
                    );
                """);

        // Create spring_session_attributes table (your JSON setup)
        jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS spring_session_attributes (
                        SESSION_PRIMARY_ID CHAR(36) NOT NULL,
                        ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
                        ATTRIBUTE_BYTES TEXT,
                        CONSTRAINT spring_session_attributes_pkey PRIMARY KEY(SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
                        CONSTRAINT fk_session FOREIGN KEY(SESSION_PRIMARY_ID) REFERENCES spring_session(PRIMARY_ID) ON DELETE CASCADE
                    );
                """);
    }
}
