package com.otilm.core.auth;

import com.otilm.api.model.core.auth.AuthActionDto;
import com.otilm.api.model.core.auth.AuthResourceDto;
import com.otilm.api.model.core.auth.ResourcePermissionsDto;
import com.otilm.api.model.core.auth.ResourcePermissionsRequestDto;
import com.otilm.api.model.core.auth.RolePermissionsRequestDto;
import com.otilm.api.model.core.auth.SubjectPermissionsDto;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.model.auth.ResourceSyncRequestDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns the resource/action catalogue the startup scan produces into the permission set of a role that may read
 * everything and change nothing. Deriving it means a resource or action added anywhere in the platform lands on the
 * right side of the boundary the moment it is annotated, instead of waiting for someone to remember this role.
 * <p>
 * A pure function over the scanned catalogue, deliberately free of Spring and of the auth-service client, so the
 * whole boundary can be pinned by unit tests - it is the half of the reconciliation where a mistake silently grants
 * write access.
 */
public final class ReadOnlyRolePermissions {

    /**
     * The action codes a read-only role may hold, taken from {@link ResourceAction#isGrantableToReadOnlyRole()} so
     * that classifying a new action on the enum is the only step needed. Membership is the whole rule: a code that
     * is not here is either a write, a sensitive read that discloses stored secret material, a sentinel the auth
     * service has no action for, or a code this platform version no longer knows - and none of those is grantable.
     */
    private static final Set<String> READ_ONLY_ACTION_CODES = Arrays.stream(ResourceAction.values())
            .filter(ResourceAction::isGrantableToReadOnlyRole)
            .map(ResourceAction::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private ReadOnlyRolePermissions() {
    }

    /** Derives the payload from the catalogue the startup annotation scan produced. */
    public static RolePermissionsRequestDto deriveFrom(List<ResourceSyncRequestDto> catalogue) {
        Map<String, List<String>> actionCodesByResource = new LinkedHashMap<>();
        for (ResourceSyncRequestDto scanned : catalogue) {
            actionCodesByResource.put(scanned.getName().getCode(),
                    scanned.getActions() == null ? List.of() : scanned.getActions());
        }
        return derive(actionCodesByResource);
    }

    /**
     * Derives the payload from the catalogue the auth service already holds, for the migration that seeds the role -
     * granting only pairs the auth service knows, which is what it accepts.
     */
    public static RolePermissionsRequestDto deriveFromAuthResources(List<AuthResourceDto> catalogue) {
        Map<String, List<String>> actionCodesByResource = new LinkedHashMap<>();
        for (AuthResourceDto resource : catalogue) {
            actionCodesByResource.put(resource.getName(), resource.getActions() == null ? List.of()
                    : resource.getActions().stream().map(AuthActionDto::getName).toList());
        }
        return derive(actionCodesByResource);
    }

    /**
     * Builds the full-replacement permission payload for a role that may read everything and change nothing.
     * <p>
     * A resource left with no grantable action is dropped rather than emitted with an empty action list, because
     * the auth service reads an empty list next to {@code allowAllActions} as every action on that resource - the
     * exact inverse of a read-only grant.
     * <p>
     * Actions come out deduplicated and sorted, so an unchanged catalogue yields an identical payload however the
     * catalogue happened to order it.
     */
    private static RolePermissionsRequestDto derive(Map<String, List<String>> actionCodesByResource) {
        List<ResourcePermissionsRequestDto> resources = new ArrayList<>();
        actionCodesByResource.forEach((resourceCode, actionCodes) -> {
            List<String> granted = actionCodes.stream()
                    .filter(READ_ONLY_ACTION_CODES::contains)
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .toList();
            if (!granted.isEmpty()) {
                resources.add(readOnlyGrant(resourceCode, granted));
            }
        });

        RolePermissionsRequestDto derived = new RolePermissionsRequestDto();
        derived.setAllowAllResources(false);
        derived.setResources(resources);
        return derived;
    }

    /**
     * Whether the role already holds exactly the derived grants, so the reconciliation can leave it alone and stay
     * quiet. Compares grants rather than payloads: action order is the auth service's own and means nothing, while
     * an object-level entry - which the derivation never emits - is a grant that has to go.
     */
    public static boolean matches(RolePermissionsRequestDto derived, SubjectPermissionsDto stored) {
        if (stored == null) {
            return false;
        }
        if (Boolean.TRUE.equals(derived.getAllowAllResources()) != Boolean.TRUE.equals(stored.getAllowAllResources())) {
            return false;
        }

        Map<String, String> derivedGrants = new TreeMap<>();
        for (ResourcePermissionsRequestDto resource : derived.getResources()) {
            derivedGrants.put(resource.getName(),
                    grantSignature(resource.getAllowAllActions(), resource.getActions(), resource.getObjects()));
        }
        Map<String, String> storedGrants = new TreeMap<>();
        for (ResourcePermissionsDto resource : stored.getResources()) {
            storedGrants.put(resource.getName(),
                    grantSignature(resource.getAllowAllActions(), resource.getActions(), resource.getObjects()));
        }
        return derivedGrants.equals(storedGrants);
    }

    public static int countActions(RolePermissionsRequestDto permissions) {
        return permissions.getResources().stream().mapToInt(resource -> resource.getActions().size()).sum();
    }

    private static String grantSignature(Boolean allowAllActions, List<String> actions, List<?> objects) {
        String actionSignature = Boolean.TRUE.equals(allowAllActions)
                ? "*"
                : String.join(",", actions == null ? List.of() : actions.stream().sorted().toList());
        return objects == null || objects.isEmpty() ? actionSignature : actionSignature + "+objects";
    }

    private static ResourcePermissionsRequestDto readOnlyGrant(String resourceName, List<String> actions) {
        ResourcePermissionsRequestDto permissions = new ResourcePermissionsRequestDto();
        permissions.setName(resourceName);
        permissions.setAllowAllActions(false);
        permissions.setActions(actions);
        permissions.setObjects(List.of());
        return permissions;
    }
}
