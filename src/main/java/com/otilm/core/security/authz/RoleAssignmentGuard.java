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
 * Decides which roles may be attached to which users. Role membership is editable from two directions -
 * role -&gt; users and user -&gt; roles - and both must enforce the same rules, otherwise the weaker endpoint
 * becomes the way around the stronger one.
 * <p>
 * Two roles are refused:
 * <ul>
 *     <li>a role paired with a system user, which belongs to that identity and never to an operator;</li>
 *     <li>a role granting access to all resources, unless the caller already holds it - without this,
 *     {@code USER:UPDATE} would be enough to grant oneself full platform administration.</li>
 * </ul>
 * Every other role, including read-only system roles such as {@code auditor}, is assignable as before.
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
        if (isPairedWithSystemUser(role)) {
            rejectHumanMembers(role, userUuids);
        }
        if (grantsAllResources(role.getUuid())) {
            requireCallerHoldsRole(role);
        }
    }

    /**
     * A membership update replaces the whole list, so an omitted system user is a detached one. Allowing that
     * would both strand the identity that depends on the role and unpair the role, leaving the rule above with
     * nothing to recognise on a second call.
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
     * The pairing is read from the auth service instead of a hardcoded name list, so that the roles seeded
     * alongside a system user (acme, scep, cmp, localhost, attribute-content-resolver) are recognised while
     * {@code superadmin} - a system role that carries no system user - stays assignable to operators.
     */
    private static boolean isPairedWithSystemUser(RoleDetailDto role) {
        return role.getUsers() != null && role.getUsers().stream().anyMatch(RoleAssignmentGuard::isSystemUser);
    }

    private void rejectHumanMembers(RoleDetailDto role, List<String> userUuids) {
        Set<String> systemMembers = role.getUsers().stream()
                .filter(RoleAssignmentGuard::isSystemUser)
                .map(UserDto::getUuid)
                .collect(Collectors.toSet());

        for (String userUuid : userUuids) {
            if (systemMembers.contains(userUuid)) {
                continue;
            }
            UserDetailDto user = userManagementApiClient.getUserDetail(userUuid);
            if (!isSystemUser(user)) {
                throw new ValidationException(
                        "Role '%s' belongs to a system user and cannot be assigned to user '%s'."
                                .formatted(role.getName(), user.getUsername()));
            }
        }
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
        return Boolean.TRUE.equals(user.getSystemUser());
    }
}
