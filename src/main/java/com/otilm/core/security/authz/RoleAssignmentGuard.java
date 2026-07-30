package com.otilm.core.security.authz;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.core.auth.RoleDetailDto;
import com.otilm.api.model.core.auth.SubjectPermissionsDto;
import com.otilm.api.model.core.auth.UserDetailDto;
import com.otilm.api.model.core.auth.UserDto;
import com.otilm.core.security.authn.client.RoleManagementApiClient;
import com.otilm.core.security.authn.client.UserManagementApiClient;
import com.otilm.core.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which roles may be attached to which users. Membership is editable from both directions, so both enforce
 * the same rules — otherwise the weaker endpoint is the way around the stronger one. A role paired with a system
 * user is refused, as is a role granting all resources unless the caller already holds it; without the latter,
 * {@code USER:UPDATE} alone would grant full platform administration. Everything else, {@code auditor} included,
 * stays assignable.
 */
@Component
public class RoleAssignmentGuard {

    private RoleManagementApiClient roleManagementApiClient;
    private UserManagementApiClient userManagementApiClient;

    @Autowired
    public void setRoleManagementApiClient(RoleManagementApiClient roleManagementApiClient) {
        this.roleManagementApiClient = roleManagementApiClient;
    }

    @Autowired
    public void setUserManagementApiClient(UserManagementApiClient userManagementApiClient) {
        this.userManagementApiClient = userManagementApiClient;
    }

    /** Guards the role -&gt; users direction: the users that would end up holding {@code roleUuid}. */
    public void checkUsersAssignableToRole(String roleUuid, List<String> userUuids) {
        List<String> members = userUuids == null ? List.of() : userUuids;
        RoleDetailDto role = roleManagementApiClient.getRoleDetail(roleUuid);
        checkAssignment(role, members);
        if (isPairedWithSystemUser(role)) {
            requireSystemMembersRetained(role, members);
        }
    }

    /** Guards the user -&gt; roles direction: the roles that would end up held by {@code userUuid}. */
    public void checkRolesAssignableToUser(String userUuid, List<String> roleUuids) {
        if (roleUuids == null) {
            return;
        }
        for (String roleUuid : roleUuids) {
            checkAssignment(roleManagementApiClient.getRoleDetail(roleUuid), List.of(userUuid));
        }
    }

    private void checkAssignment(RoleDetailDto role, List<String> userUuids) {
        checkMembers(role, userUuids);
        if (grantsAllResources(role.getUuid())) {
            requireCallerHoldsRole(role);
        }
    }

    /**
     * A system user holds the role seeded with it and no other, and that role takes no other members: the pairing is
     * the identity's whole permission boundary, so an editable half either widens it or hands it to an operator.
     */
    private void checkMembers(RoleDetailDto role, List<String> userUuids) {
        Set<String> systemMembers = systemMemberUuids(role);

        for (String userUuid : userUuids) {
            if (systemMembers.contains(userUuid)) {
                continue;
            }
            UserDetailDto user = userManagementApiClient.getUserDetail(userUuid);
            if (isSystemUser(user)) {
                throw new ValidationException(
                        "System user '%s' holds only its own role and cannot be added to role '%s'."
                                .formatted(user.getUsername(), role.getName()));
            }
            if (!systemMembers.isEmpty()) {
                throw new ValidationException(
                        "Role '%s' belongs to a system user and cannot be assigned to user '%s'."
                                .formatted(role.getName(), user.getUsername()));
            }
        }
    }

    private static Set<String> systemMemberUuids(RoleDetailDto role) {
        if (role.getUsers() == null) {
            return Set.of();
        }
        return role.getUsers().stream()
                .filter(RoleAssignmentGuard::isSystemUser)
                .map(UserDto::getUuid)
                .collect(Collectors.toSet());
    }

    /**
     * A membership update replaces the whole list, so an omitted system user is a detached one — stranding that
     * identity and unpairing the role, leaving the rule above nothing to recognise on a second call.
     */
    private static void requireSystemMembersRetained(RoleDetailDto role, List<String> userUuids) {
        for (UserDto member : role.getUsers()) {
            if (isSystemUser(member) && !userUuids.contains(member.getUuid())) {
                throw new ValidationException(
                        "Role '%s' belongs to system user '%s', which cannot be removed from it."
                                .formatted(role.getName(), member.getUsername()));
            }
        }
    }

    /**
     * Read from the auth service rather than a hardcoded name list, so roles seeded alongside a system user are
     * recognised while {@code superadmin} — a system role carrying no system user — stays assignable.
     */
    private static boolean isPairedWithSystemUser(RoleDetailDto role) {
        return role.getUsers() != null && role.getUsers().stream().anyMatch(RoleAssignmentGuard::isSystemUser);
    }

    private boolean grantsAllResources(String roleUuid) {
        SubjectPermissionsDto permissions = roleManagementApiClient.getPermissions(roleUuid);
        return permissions != null && Boolean.TRUE.equals(permissions.getAllowAllResources());
    }

    private static void requireCallerHoldsRole(RoleDetailDto role) {
        if (!callerRoleUuids().contains(role.getUuid())) {
            throw new ValidationException(
                    "Role '%s' grants access to all resources and can only be assigned by a user who already holds it."
                            .formatted(role.getName()));
        }
    }

    private static Set<String> callerRoleUuids() {
        List<NameAndUuidDto> roles;
        try {
            roles = AuthHelper.getUserProfile().getRoles();
        } catch (ValidationException e) {
            // An unidentifiable caller holds no roles, so it fails the check like any other caller without the role.
            return Set.of();
        }
        return roles == null ? Set.of() : roles.stream().map(NameAndUuidDto::getUuid).collect(Collectors.toSet());
    }

    private static boolean isSystemUser(UserDto user) {
        return user != null && Boolean.TRUE.equals(user.getSystemUser());
    }
}
