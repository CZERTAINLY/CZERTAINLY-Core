package db.migration;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.ResourceSyncRequestDto;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.DatabaseAuthMigration;
import com.czertainly.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Migration script for the Access control changes.
 * The script will take the data from the core database and trigger API calls to the
 * Auth Service for updating ACME and SCEP role, to add all necessary certificate permissions
 */
public class V202308050825__UpdateAcmeScepRolesPermissions extends BaseJavaMigration {
    private final List<String> certificatePermissions = List.of(
            ResourceAction.CREATE.getCode(),
            ResourceAction.ISSUE.getCode(),
            ResourceAction.DETAIL.getCode(),
            ResourceAction.UPDATE.getCode(),
            ResourceAction.RENEW.getCode(),
            ResourceAction.REVOKE.getCode(),
            ResourceAction.LIST.getCode());

    private WebClient client;
    private RoleManagementApiClient roleManagementApiClient;
    private ResourceApiClient resourceApiClient;

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202308050825__UpdateAcmeScepRolesPermissions.getChecksum();
    }

    public void migrate(Context context) throws Exception {
        String authUrl = DatabaseAuthMigration.getAuthServiceUrl();
        roleManagementApiClient = new RoleManagementApiClient(authUrl, client);
        resourceApiClient = new ResourceApiClient(authUrl, client);

        // seed issue action for certificate
        List<ResourceSyncRequestDto> resources = new ArrayList<>();
        ResourceSyncRequestDto resourceSyncRequestDto = new ResourceSyncRequestDto();
        resourceSyncRequestDto.setName(com.czertainly.core.model.auth.Resource.findByCode(com.czertainly.api.model.core.auth.Resource.CERTIFICATE.getCode()));
        resourceSyncRequestDto.setActions(List.of(ResourceAction.ISSUE.getCode()));
        resources.add(resourceSyncRequestDto);
        resourceApiClient.addResources(resources);

        // get ACME and SCEP roles
        RoleDto acmeRole = null;
        RoleDto scepRole = null;
        List<RoleDto> roles = roleManagementApiClient.getRoles().getData();
        for (RoleDto role: roles) {
            if (role.getSystemRole()) {
                if (role.getName().equals(AuthHelper.ACME_USERNAME)) acmeRole = role;
                else if (role.getName().equals(AuthHelper.SCEP_USERNAME)) scepRole = role;
            }
        }
        if (acmeRole == null || scepRole == null) throw new ValidationException("Migration cannot find system ACME or SCEP role");

        // update certificate permissions
        updatedPermissions(acmeRole);
        updatedPermissions(scepRole);
    }

    private void updatedPermissions(RoleDto role) {
        SubjectPermissionsDto permissions = roleManagementApiClient.getPermissions(role.getUuid());

        RolePermissionsRequestDto request = new RolePermissionsRequestDto();
        List<ResourcePermissionsRequestDto> resourcePermissionsRequests = new ArrayList<>();
        request.setAllowAllResources(permissions.getAllowAllResources());
        for (ResourcePermissionsDto resourcePermissions: permissions.getResources()) {
            ResourcePermissionsRequestDto requestDto = new ResourcePermissionsRequestDto();
            requestDto.setName(resourcePermissions.getName());
            requestDto.setActions(resourcePermissions.getName().equals(com.czertainly.api.model.core.auth.Resource.CERTIFICATE.getCode()) ? certificatePermissions : resourcePermissions.getActions());
            requestDto.setAllowAllActions(resourcePermissions.getAllowAllActions());
            requestDto.setObjects(List.of());
            resourcePermissionsRequests.add(requestDto);
        }
        request.setResources(resourcePermissionsRequests);
        roleManagementApiClient.savePermissions(role.getUuid(), request);
    }
}