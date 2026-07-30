package com.otilm.core.auth;

import com.otilm.api.model.core.auth.RoleDto;
import com.otilm.api.model.core.auth.RolePermissionsRequestDto;
import com.otilm.api.model.core.auth.RoleWithPaginationDto;
import com.otilm.core.model.auth.ResourceSyncRequestDto;
import com.otilm.core.model.auth.SyncResponseDto;
import com.otilm.core.security.authn.client.ResourceApiClient;
import com.otilm.core.security.authn.client.RoleManagementApiClient;
import com.otilm.core.util.AuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.List;


@Component
public class AuthResourceSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(AuthResourceSynchronizer.class);
    private ContextRefreshListener contextRefreshListener;
    private ResourceApiClient resourceApiClient;
    private RoleManagementApiClient roleManagementApiClient;

    @Autowired
    public void setEndpointsListener(ContextRefreshListener contextRefreshListener) {
        this.contextRefreshListener = contextRefreshListener;
    }

    @Autowired
    public void setEndPointApiClient(ResourceApiClient resourceApiClient) {
        this.resourceApiClient = resourceApiClient;
    }

    @Autowired
    public void setRoleManagementApiClient(RoleManagementApiClient roleManagementApiClient) {
        this.roleManagementApiClient = roleManagementApiClient;
    }

    @EventListener({ApplicationReadyEvent.class})
    public void register() {
        logger.info("Initiating Endpoints sync");
        List<ResourceSyncRequestDto> resources = contextRefreshListener.getResources();
        logger.debug("Resources: {}", resources);
        //Sync API Operation here
        try {
            SyncResponseDto response = resourceApiClient.syncResources(resources);
            logger.info("Sync operation completed, Response is {}", response);
            reconcileAuditorRole(resources);
        } catch (WebClientRequestException e) {
            logger.error("Unable to communicate with Auth Service: {}", e.getMessage());
        }
    }

    /**
     * Rebuilds the auditor role from the catalogue this boot just synced. Runs only after {@code syncResources}
     * succeeded, since the auth service rejects a permission naming a resource or action it does not yet know.
     * Best effort: a role the platform can start without must never stop it starting.
     */
    private void reconcileAuditorRole(List<ResourceSyncRequestDto> resources) {
        try {
            String roleUuid = findAuditorRoleUuid();
            if (roleUuid == null) {
                logger.info("System role '{}' not found in Auth Service, its permissions were left untouched",
                        AuthHelper.AUDITOR_ROLE_NAME);
                return;
            }

            RolePermissionsRequestDto derived = ReadOnlyRolePermissions.deriveFrom(resources);
            if (ReadOnlyRolePermissions.matches(derived, roleManagementApiClient.getPermissions(roleUuid))) {
                logger.debug("System role '{}' already holds the permissions derived from the resource catalogue",
                        AuthHelper.AUDITOR_ROLE_NAME);
                return;
            }

            roleManagementApiClient.savePermissions(roleUuid, derived);
            logger.info("System role '{}' permissions rebuilt from the resource catalogue: {} resources, {} actions",
                    AuthHelper.AUDITOR_ROLE_NAME, derived.getResources().size(),
                    ReadOnlyRolePermissions.countActions(derived));
        } catch (Exception e) {
            logger.error("Unable to reconcile permissions of system role '{}': {}",
                    AuthHelper.AUDITOR_ROLE_NAME, e.getMessage());
        }
    }

    /** Matched among the system roles by name, the way the migrations resolve the identities they seeded. */
    private String findAuditorRoleUuid() {
        RoleWithPaginationDto roles = roleManagementApiClient.getRoles();
        if (roles == null || roles.getData() == null) {
            return null;
        }
        return roles.getData().stream()
                .filter(role -> Boolean.TRUE.equals(role.getSystemRole()))
                .filter(role -> AuthHelper.AUDITOR_ROLE_NAME.equals(role.getName()))
                .map(RoleDto::getUuid)
                .findFirst()
                .orElse(null);
    }
}
