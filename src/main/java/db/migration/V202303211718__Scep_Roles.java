package db.migration;

import com.czertainly.api.model.core.auth.*;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.ResourceSyncRequestDto;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.util.DatabaseAuthMigration;
import com.czertainly.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Migration script for the Access control changes.
 * The script will take the data from the core database and trigger API calls to the
 * Auth Service for creating SCEP role, user and the permissions
 */
public class V202303211718__Scep_Roles extends BaseJavaMigration {

    private final List<String> detailPermissionObjects = List.of(Resource.SCEP_PROFILE.getCode(),
            Resource.RA_PROFILE.getCode(),
            Resource.AUTHORITY.getCode());
    private final List<String> certificatePermissions = List.of(ResourceAction.CREATE.getCode(),
            ResourceAction.DETAIL.getCode(),
            ResourceAction.UPDATE.getCode(),
            ResourceAction.RENEW.getCode(),
            ResourceAction.REVOKE.getCode(),
            ResourceAction.LIST.getCode());
    private final Map<String, String> detailPermissionObjectsListingEndpoints = Map.of(
            Resource.SCEP_PROFILE.getCode(), "/v1/scepProfiles",
            Resource.RA_PROFILE.getCode(), "/v1/raProfiles",
            Resource.AUTHORITY.getCode(), "/v1/authorities");
    private WebClient client;
    private UserManagementApiClient userManagementApiClient;
    private RoleManagementApiClient roleManagementApiClient;
    private ResourceApiClient resourceApiClient;

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202209211100__Access_Control.getChecksum();
    }

    public void migrate(Context context) throws Exception {
        String authUrl = DatabaseAuthMigration.getAuthServiceUrl();
        userManagementApiClient = new UserManagementApiClient(authUrl, client);
        roleManagementApiClient = new RoleManagementApiClient(authUrl, client);
        resourceApiClient = new ResourceApiClient(authUrl, client);
        try (Statement select = context.getConnection().createStatement()) {
            //Permissions will be seeded to the auth service for initial SCEP Role registration
            seedResources();
            createScepRole();
        }
    }

    private void assignRoles(String userUuid, String roleUUid) {
        userManagementApiClient.updateRole(userUuid, roleUUid);
    }

    private void seedResources() {
        List<ResourceSyncRequestDto> resources = new ArrayList<>();

        for (String permissionObject : detailPermissionObjects) {
            ResourceSyncRequestDto requestDto = new ResourceSyncRequestDto();
            requestDto.setName(Resource.findByCode(permissionObject));
            requestDto.setActions(List.of(ResourceAction.DETAIL.getCode(), ResourceAction.LIST.getCode()));
            requestDto.setListObjectsEndpoint(detailPermissionObjectsListingEndpoints.get(permissionObject));
            resources.add(requestDto);
        }

        //Certificate Operations
        ResourceSyncRequestDto requestDto = new ResourceSyncRequestDto();
        requestDto.setName(Resource.CERTIFICATE);
        requestDto.setActions(certificatePermissions);

        resources.add(requestDto);

        resourceApiClient.addResources(resources);
    }

    private RolePermissionsRequestDto getPermissionPayload() {
        RolePermissionsRequestDto request = new RolePermissionsRequestDto();
        request.setAllowAllResources(false);

        List<ResourcePermissionsRequestDto> resourcePermissionsRequestDtos = new ArrayList<>();

        for (String permissionObject : detailPermissionObjects) {
            ResourcePermissionsRequestDto requestDto = new ResourcePermissionsRequestDto();
            requestDto.setName(permissionObject);
            requestDto.setActions(List.of(ResourceAction.DETAIL.getCode(), ResourceAction.LIST.getCode()));
            requestDto.setAllowAllActions(false);
            requestDto.setObjects(List.of());
            resourcePermissionsRequestDtos.add(requestDto);
        }

        //Add Certificate Permissions
        ResourcePermissionsRequestDto clientOperationsRequestDto = new ResourcePermissionsRequestDto();
        clientOperationsRequestDto.setName(Resource.CERTIFICATE.getCode());
        clientOperationsRequestDto.setActions(certificatePermissions);
        clientOperationsRequestDto.setAllowAllActions(false);
        clientOperationsRequestDto.setObjects(List.of());
        resourcePermissionsRequestDtos.add(clientOperationsRequestDto);

        request.setResources(resourcePermissionsRequestDtos);
        return request;
    }

    private void createScepRole() {
        RoleRequestDto requestDto = new RoleRequestDto();
        requestDto.setName("scep");
        requestDto.setDescription("System role with all SCEP permissions");
        requestDto.setSystemRole(true);
        requestDto.setPermissions(getPermissionPayload());
        RoleDetailDto response = roleManagementApiClient.createRole(requestDto);
        String scepUser = createScepUser();
        assignRoles(scepUser, response.getUuid());
    }

    private String createScepUser() {
        UserRequestDto requestDto = new UserRequestDto();
        requestDto.setUsername("scep");
        requestDto.setDescription("System User for SCEP Operations");
        requestDto.setEnabled(true);
        requestDto.setSystemUser(true);
        requestDto.setCertificateFingerprint("");
        return userManagementApiClient.createUser(requestDto).getUuid();
    }
}