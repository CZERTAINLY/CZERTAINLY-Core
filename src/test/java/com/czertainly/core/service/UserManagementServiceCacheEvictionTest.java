package com.czertainly.core.service;

import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.security.authn.client.AuthenticationCache;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.SessionTableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

class UserManagementServiceCacheEvictionTest extends BaseSpringBootTest {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserManagementApiClient userManagementApiClient;

    @MockitoBean
    private AuthenticationCache authenticationCache;

    @BeforeEach
    void setupSessionTables() {
        SessionTableHelper.createSessionTables(jdbcTemplate);
    }

    @Test
    void updateUser_withoutCertificate_evictsWithNullFingerprint() throws Exception {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateUser(Mockito.eq(userUuid), Mockito.any()))
                .thenReturn(userDetailDto(userUuid));

        UpdateUserRequestDto request = new UpdateUserRequestDto();
        request.setCustomAttributes(List.of());

        // when
        userManagementService.updateUser(userUuid, request);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, null);
    }

    @Test
    void updateUser_withCertificate_evictsWithCertificateFingerprint() throws Exception {
        // given
        String userUuid = UUID.randomUUID().toString();
        String fingerprint = "test-fingerprint";
        createCertificateForUser(UUID.fromString(userUuid), fingerprint);
        Mockito.when(userManagementApiClient.updateUser(Mockito.eq(userUuid), Mockito.any()))
                .thenReturn(userDetailDto(userUuid));

        UpdateUserRequestDto request = new UpdateUserRequestDto();
        request.setCustomAttributes(List.of());

        // when
        userManagementService.updateUser(userUuid, request);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, fingerprint);
    }

    @Test
    void updateUserInternal_evictsUserCache() throws Exception {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateUser(Mockito.eq(userUuid), Mockito.any()))
                .thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.updateUserInternal(userUuid, new UpdateUserRequestDto(), "", "");

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, null);
    }

    @Test
    void deleteUser_withCertificate_capturesFingerprintBeforeAssociationIsRemoved() {
        // given – certificate is associated with the user before deletion
        String userUuid = UUID.randomUUID().toString();
        String fingerprint = "test-fingerprint";
        createCertificateForUser(UUID.fromString(userUuid), fingerprint);

        // when
        userManagementService.deleteUser(userUuid);

        // then – fingerprint was captured before removeCertificateUser severed the association,
        // so the cache entry for the old certificate is correctly evicted
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, fingerprint);
    }

    @Test
    void deleteUser_withoutCertificate_evictsWithNullFingerprint() {
        // given
        String userUuid = UUID.randomUUID().toString();

        // when
        userManagementService.deleteUser(userUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, null);
    }

    @Test
    void disableUser_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.disableUser(userUuid)).thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.disableUser(userUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, null);
    }

    @Test
    void updateRoles_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateRoles(Mockito.eq(userUuid), Mockito.any()))
                .thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.updateRoles(userUuid, List.of());

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, null);
    }

    @Test
    void updateRole_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        String roleUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateRole(userUuid, roleUuid)).thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.updateRole(userUuid, roleUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, null);
    }

    @Test
    void removeRole_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        String roleUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.removeRole(userUuid, roleUuid)).thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.removeRole(userUuid, roleUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid, null);
    }

    private void createCertificateForUser(UUID userUuid, String fingerprint) {
        CertificateContent content = new CertificateContent();
        content.setContent("dummy");
        certificateContentRepository.save(content);

        Certificate cert = new Certificate();
        cert.setUserUuid(userUuid);
        cert.setFingerprint(fingerprint);
        cert.setCertificateContent(content);
        certificateRepository.save(cert);
    }

    private static UserDetailDto userDetailDto(String uuid) {
        UserDetailDto dto = new UserDetailDto();
        dto.setUuid(uuid);
        dto.setUsername("user-" + uuid);
        return dto;
    }
}
