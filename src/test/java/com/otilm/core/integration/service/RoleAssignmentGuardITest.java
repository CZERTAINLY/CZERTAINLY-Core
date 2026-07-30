package com.otilm.core.integration.service;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.core.auth.RoleDetailDto;
import com.otilm.api.model.core.auth.SubjectPermissionsDto;
import com.otilm.api.model.core.auth.UserDetailDto;
import com.otilm.api.model.core.auth.UserDto;
import com.otilm.api.model.core.auth.UserProfileDto;
import com.otilm.api.model.core.logging.enums.AuthMethod;
import com.otilm.core.security.authn.PlatformAuthenticationToken;
import com.otilm.core.security.authn.PlatformUserDetails;
import com.otilm.core.security.authn.client.AuthenticationCache;
import com.otilm.core.security.authn.client.AuthenticationInfo;
import com.otilm.core.security.authn.client.RoleManagementApiClient;
import com.otilm.core.security.authn.client.UserManagementApiClient;
import com.otilm.core.service.RoleManagementExternalService;
import com.otilm.core.service.UserManagementExternalService;
import com.otilm.core.util.AuthHelper;
import com.otilm.core.util.BaseSpringBootTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the guard that decides which roles may be attached to which users, from both directions:
 * role -> users ({@link RoleManagementExternalService#updateUsers}) and user -> roles
 * ({@link UserManagementExternalService#updateRoles} / {@code updateRole}).
 */
class RoleAssignmentGuardITest extends BaseSpringBootTest {

    private static final String HUMAN_USERNAME = "operator";

    @Autowired
    private RoleManagementExternalService roleManagementService;

    @Autowired
    private UserManagementExternalService userManagementService;

    @MockitoBean
    private RoleManagementApiClient roleManagementApiClient;

    @MockitoBean
    private UserManagementApiClient userManagementApiClient;

    @MockitoBean
    private AuthenticationCache authenticationCache;

    // Rule 1: a role paired with a system user is never assignable to a human user.

    /**
     * Membership updates replace the whole list, so a caller could otherwise detach the system user first and
     * attach a human on a second call, once the role no longer looks paired.
     */
    @Test
    void updateUsers_rejectsDetachingTheSystemUserFromItsRole() {
        String roleUuid = UUID.randomUUID().toString();
        when(roleManagementApiClient.getRoleDetail(roleUuid))
                .thenReturn(role(roleUuid, AuthHelper.ACME_USERNAME, true, List.of(systemUser(AuthHelper.ACME_USERNAME))));

        ValidationException exception = Assertions.assertThrows(ValidationException.class,
                () -> roleManagementService.updateUsers(roleUuid, List.of()));

        Assertions.assertTrue(exception.getMessage().contains(AuthHelper.ACME_USERNAME), exception.getMessage());
        verify(roleManagementApiClient, never()).updateUsers(any(), any());
    }

    @Test
    void updateUsers_rejectsHumanUserOnRolePairedWithSystemUser() {
        String roleUuid = UUID.randomUUID().toString();
        String humanUuid = UUID.randomUUID().toString();
        when(roleManagementApiClient.getRoleDetail(roleUuid))
                .thenReturn(role(roleUuid, AuthHelper.ACME_USERNAME, true, List.of(systemUser(AuthHelper.ACME_USERNAME))));
        when(userManagementApiClient.getUserDetail(humanUuid)).thenReturn(humanUser(humanUuid));

        ValidationException exception = Assertions.assertThrows(ValidationException.class,
                () -> roleManagementService.updateUsers(roleUuid, List.of(humanUuid)));

        Assertions.assertTrue(exception.getMessage().contains(AuthHelper.ACME_USERNAME), exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains(HUMAN_USERNAME), exception.getMessage());
        verify(roleManagementApiClient, never()).updateUsers(any(), any());
    }

    @Test
    void updateRole_rejectsRolePairedWithSystemUserForHumanUser() {
        String roleUuid = UUID.randomUUID().toString();
        String humanUuid = UUID.randomUUID().toString();
        when(roleManagementApiClient.getRoleDetail(roleUuid))
                .thenReturn(role(roleUuid, AuthHelper.ACME_USERNAME, true, List.of(systemUser(AuthHelper.ACME_USERNAME))));
        when(userManagementApiClient.getUserDetail(humanUuid)).thenReturn(humanUser(humanUuid));

        ValidationException exception = Assertions.assertThrows(ValidationException.class,
                () -> userManagementService.updateRole(humanUuid, roleUuid));

        Assertions.assertTrue(exception.getMessage().contains(AuthHelper.ACME_USERNAME), exception.getMessage());
        verify(userManagementApiClient, never()).updateRole(any(), any());
    }

    // Rule 2: a role that allows all resources may only be assigned by someone who already holds it.

    @Test
    void updateRoles_rejectsAllowAllResourcesRoleWhenCallerDoesNotHoldIt() {
        String roleUuid = UUID.randomUUID().toString();
        String targetUuid = UUID.randomUUID().toString();
        when(roleManagementApiClient.getRoleDetail(roleUuid))
                .thenReturn(role(roleUuid, AuthHelper.SUPERADMIN_USERNAME, true, List.of()));
        when(roleManagementApiClient.getPermissions(roleUuid)).thenReturn(permissions(true));

        ValidationException exception = Assertions.assertThrows(ValidationException.class,
                () -> userManagementService.updateRoles(targetUuid, List.of(roleUuid)));

        Assertions.assertTrue(exception.getMessage().contains(AuthHelper.SUPERADMIN_USERNAME), exception.getMessage());
        verify(userManagementApiClient, never()).updateRoles(any(), any());
    }

    @Test
    void updateRoles_allowsAllowAllResourcesRoleWhenCallerHoldsIt() {
        String roleUuid = UUID.randomUUID().toString();
        String targetUuid = UUID.randomUUID().toString();
        when(roleManagementApiClient.getRoleDetail(roleUuid))
                .thenReturn(role(roleUuid, AuthHelper.SUPERADMIN_USERNAME, true, List.of()));
        when(roleManagementApiClient.getPermissions(roleUuid)).thenReturn(permissions(true));
        when(userManagementApiClient.updateRoles(eq(targetUuid), any())).thenReturn(humanUser(targetUuid));
        authenticateHoldingRole(roleUuid, AuthHelper.SUPERADMIN_USERNAME);

        userManagementService.updateRoles(targetUuid, List.of(roleUuid));

        verify(userManagementApiClient).updateRoles(targetUuid, List.of(roleUuid));
    }

    @Test
    void updateUsers_rejectsAllowAllResourcesRoleWhenCallerDoesNotHoldIt() {
        String roleUuid = UUID.randomUUID().toString();
        String humanUuid = UUID.randomUUID().toString();
        when(roleManagementApiClient.getRoleDetail(roleUuid))
                .thenReturn(role(roleUuid, AuthHelper.SUPERADMIN_USERNAME, true, List.of()));
        when(roleManagementApiClient.getPermissions(roleUuid)).thenReturn(permissions(true));
        when(userManagementApiClient.getUserDetail(humanUuid)).thenReturn(humanUser(humanUuid));

        Assertions.assertThrows(ValidationException.class,
                () -> roleManagementService.updateUsers(roleUuid, List.of(humanUuid)));

        verify(roleManagementApiClient, never()).updateUsers(any(), any());
    }

    // Rule 3: every other role - auditor included, system role though it is - stays assignable.

    @Test
    void updateRole_allowsAuditorSystemRole() {
        String roleUuid = UUID.randomUUID().toString();
        String humanUuid = UUID.randomUUID().toString();
        when(roleManagementApiClient.getRoleDetail(roleUuid)).thenReturn(role(roleUuid, "auditor", true, List.of()));
        when(roleManagementApiClient.getPermissions(roleUuid)).thenReturn(permissions(false));
        when(userManagementApiClient.getUserDetail(humanUuid)).thenReturn(humanUser(humanUuid));
        when(userManagementApiClient.updateRole(humanUuid, roleUuid)).thenReturn(humanUser(humanUuid));

        userManagementService.updateRole(humanUuid, roleUuid);

        verify(userManagementApiClient).updateRole(humanUuid, roleUuid);
    }

    @Test
    void updateUsers_allowsAuditorSystemRole() {
        String roleUuid = UUID.randomUUID().toString();
        String humanUuid = UUID.randomUUID().toString();
        RoleDetailDto auditorRole = role(roleUuid, "auditor", true, List.of());
        when(roleManagementApiClient.getRoleDetail(roleUuid)).thenReturn(auditorRole);
        when(roleManagementApiClient.getPermissions(roleUuid)).thenReturn(permissions(false));
        when(userManagementApiClient.getUserDetail(humanUuid)).thenReturn(humanUser(humanUuid));
        when(roleManagementApiClient.updateUsers(eq(roleUuid), any())).thenReturn(auditorRole);

        roleManagementService.updateUsers(roleUuid, List.of(humanUuid));

        verify(roleManagementApiClient).updateUsers(roleUuid, List.of(humanUuid));
    }

    private void authenticateHoldingRole(String roleUuid, String roleName) {
        UserProfileDto profile = new UserProfileDto();
        UserDto caller = new UserDto();
        caller.setUuid(UUID.randomUUID().toString());
        caller.setUsername("tst-user");
        profile.setUser(caller);
        profile.setRoles(List.of(new NameAndUuidDto(roleUuid, roleName)));

        String rawData;
        try {
            rawData = new ObjectMapper().writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        AuthenticationInfo info = new AuthenticationInfo(
                AuthMethod.USER_PROXY, caller.getUuid(), caller.getUsername(), List.of(), rawData);
        SecurityContextHolder.getContext().setAuthentication(new PlatformAuthenticationToken(new PlatformUserDetails(info)));
    }

    private static RoleDetailDto role(String uuid, String name, boolean systemRole, List<UserDto> users) {
        RoleDetailDto dto = new RoleDetailDto();
        dto.setUuid(uuid);
        dto.setName(name);
        dto.setSystemRole(systemRole);
        dto.setUsers(users);
        return dto;
    }

    private static SubjectPermissionsDto permissions(boolean allowAllResources) {
        SubjectPermissionsDto dto = new SubjectPermissionsDto();
        dto.setAllowAllResources(allowAllResources);
        dto.setResources(List.of());
        return dto;
    }

    private static UserDto systemUser(String username) {
        UserDto dto = new UserDto();
        dto.setUuid(UUID.randomUUID().toString());
        dto.setUsername(username);
        dto.setSystemUser(true);
        return dto;
    }

    private static UserDetailDto humanUser(String uuid) {
        UserDetailDto dto = new UserDetailDto();
        dto.setUuid(uuid);
        dto.setUsername(HUMAN_USERNAME);
        dto.setSystemUser(false);
        dto.setRoles(List.of());
        return dto;
    }
}
