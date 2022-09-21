package db.migration;

import com.czertainly.api.model.core.auth.ObjectPermissionsRequestDto;
import com.czertainly.api.model.core.auth.ResourcePermissionsRequestDto;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.RolePermissionsRequestDto;
import com.czertainly.api.model.core.auth.RoleRequestDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Migration script for the Access control changes.
 * The script will take the data from the core database and trigger API calls to the
 * Auth Service for creating, users, roles, RA Profile Authorizations. Once the migrations are completed,
 * then the data in the core will be cleaned up.
 */
@PropertySource("classpath:application.properties")
public class V202209211100__Access_control extends BaseJavaMigration {

    private static final String AUTH_SERVICE_BASE_URL_PROPERTY = "AUTH_SERVICE_BASE_URL";
    private static final String AUTH_ADMIN_ROLE_UUID = "da5668e2-9d94-4375-98c4-d665083edceb";
    private static final String AUTH_SUPER_ADMIN_ROLE_UUID = "d34f960b-75c9-4184-ba97-665d30a9ee8a";


    private static Properties properties;
    private WebClient client;
    private UserManagementApiClient userManagementApiClient;
    private RoleManagementApiClient roleManagementApiClient;

    @Override
    public Integer getChecksum() {
        return 123456;
    }

    private List<String> certificateUserUpdateCommands = new ArrayList<>();

    public void migrate(Context context) throws Exception {
        userManagementApiClient = new UserManagementApiClient(getAuthServiceUrl(), client);
        roleManagementApiClient = new RoleManagementApiClient(getAuthServiceUrl(), client);
        try (Statement select = context.getConnection().createStatement()) {
            createCertificateUserReference(select);
            List<String> adminNames = createAdministratorUsers(context);
            Map<String, String> clientRoleMap = createClientUsers(context, adminNames);
            updateRolePermissions(context, clientRoleMap);
            executeCommands(select, certificateUserUpdateCommands);
            performCleanups(select);
        }
    }

    private void createCertificateUserReference(Statement select) throws SQLException {
        String command = "ALTER TABLE certificate add column user_uuid uuid;";
        executeCommands(select, List.of(command));
    }

    private List<String> createAdministratorUsers(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT a.username, a.name, a.surname, a.email, a.certificate_uuid, a.enabled, a.role, c.fingerprint FROM admin a JOIN certificate c ON a.certificate_uuid = c.uuid;")) {
                List<String> adminNames = new ArrayList<>();
                while (rows.next()) {
                    UserRequestDto dto = new UserRequestDto();
                    String certificateUuid = rows.getString("certificate_uuid");
                    dto.setCertificateUuid(certificateUuid);
                    dto.setLastName(rows.getString("surname"));
                    dto.setFirstName(rows.getString("name"));
                    dto.setUsername(rows.getString("username"));
                    dto.setEmail(rows.getString("email"));
                    dto.setEnabled(rows.getBoolean("enabled"));
                    dto.setCertificateFingerprint(rows.getString("fingerprint"));
                    UserDto response = userManagementApiClient.createUser(dto);
                    assignRoles(response.getUuid(), rows.getString("role").equals("SUPERADMINISTRATOR") ? AUTH_SUPER_ADMIN_ROLE_UUID : AUTH_ADMIN_ROLE_UUID);
                    adminNames.add(dto.getUsername());
                    certificateUserUpdateCommands.add("update certificate SET user_uuid = '" + response.getUuid() + "' WHERE uuid = '" + certificateUuid + "'");
                }
                return adminNames;
            }
        }
    }

    private Map<String, String> createClientUsers(Context context, List<String> adminNames) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT a.uuid, a.name, a.certificate_uuid, a.enabled, c.fingerprint FROM client a JOIN certificate c ON a.certificate_uuid = c.uuid WHERE a.certificate_uuid NOT IN (SELECT b.certificate_uuid FROM admin b);")) {
                Map<String, String> clientRoleMap = new HashMap<>();
                while (rows.next()) {
                    UserRequestDto dto = new UserRequestDto();
                    String certificateUuid = rows.getString("certificate_uuid");
                    dto.setCertificateUuid(certificateUuid);
                    String clientName = rows.getString("name");
                    if (adminNames.contains(clientName)) {
                        dto.setUsername("Client_" + clientName);
                    } else {
                        dto.setUsername(clientName);
                    }
                    dto.setEnabled(rows.getBoolean("enabled"));
                    dto.setCertificateFingerprint(rows.getString("fingerprint"));
                    UserDto response = userManagementApiClient.createUser(dto);
                    String roleUuid = createClientRoles(response.getUuid(), clientName);
                    clientRoleMap.put(rows.getString("uuid"), roleUuid);
                    certificateUserUpdateCommands.add("update certificate SET user_uuid = '" + response.getUuid() + "' WHERE uuid = '" + certificateUuid + "'");
                }
                return clientRoleMap;
            }
        }
    }

    private String getAuthServiceUrl() throws IOException, URISyntaxException {

        String authServiceUrl = System.getenv(AUTH_SERVICE_BASE_URL_PROPERTY);
        if (authServiceUrl != null && !authServiceUrl.isEmpty()) {
            return authServiceUrl;
        }

        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource("application.properties");
        File file = new File(resource.toURI());

        InputStream targetStream = new FileInputStream(file);
        Properties properties = new Properties();
        properties.load(targetStream);
        String[] splitData = ((String) properties.get("auth-service.base-url")).split(AUTH_SERVICE_BASE_URL_PROPERTY);
        return splitData[splitData.length - 1].replace("}", "");
    }

    private String createClientRoles(String clientUuid, String clientName) {
        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Client_Role_" + clientName);
        dto.setDescription("Role created during migration for the client " + clientName);
        RoleDto response = roleManagementApiClient.createRole(dto);
        assignRoles(clientUuid, response.getUuid());
        return response.getUuid();
    }

    private void assignClientPermission(String roleUuid, Set<String> raProfileUuids, Set<String> authorityUuids) {
        if (raProfileUuids.isEmpty()) return;

        RolePermissionsRequestDto roleRequest = new RolePermissionsRequestDto();
        roleRequest.setAllowAllResources(false);

        List<ResourcePermissionsRequestDto> listResources = new ArrayList<>();

        // Set RA Profile Permissions
        ResourcePermissionsRequestDto raProfileResourceRequest = new ResourcePermissionsRequestDto();
        raProfileResourceRequest.setName(Resource.RA_PROFILE.getCode());
        raProfileResourceRequest.setAllowAllActions(false);
        raProfileResourceRequest.setActions(List.of());
        List<ObjectPermissionsRequestDto> objects = new ArrayList<>();
        for (String raProfileUuid : raProfileUuids) {
            ObjectPermissionsRequestDto dto = new ObjectPermissionsRequestDto();
            dto.setUuid(raProfileUuid);
            dto.setAllow(List.of(ResourceAction.LIST.getCode(), ResourceAction.DETAIL.getCode()));
            dto.setDeny(List.of());
            objects.add(dto);
        }
        raProfileResourceRequest.setObjects(objects);
        listResources.add(raProfileResourceRequest);

        // Set Authority Permissions
        ResourcePermissionsRequestDto authorityResourceRequest = new ResourcePermissionsRequestDto();
        authorityResourceRequest.setName(Resource.AUTHORITY.getCode());
        authorityResourceRequest.setAllowAllActions(false);
        authorityResourceRequest.setActions(List.of());
        List<ObjectPermissionsRequestDto> authorityObjects = new ArrayList<>();
        for (String authorityUuid : authorityUuids) {
            ObjectPermissionsRequestDto dto = new ObjectPermissionsRequestDto();
            dto.setUuid(authorityUuid);
            dto.setAllow(List.of(ResourceAction.DETAIL.getCode()));
            dto.setDeny(List.of());
            authorityObjects.add(dto);
        }
        authorityResourceRequest.setObjects(authorityObjects);
        listResources.add(authorityResourceRequest);

        // Set Certificate Permissions
        ResourcePermissionsRequestDto certificateResourceRequest = new ResourcePermissionsRequestDto();
        certificateResourceRequest.setName(Resource.CERTIFICATE.getCode());
        certificateResourceRequest.setAllowAllActions(true);
        certificateResourceRequest.setActions(List.of());
        certificateResourceRequest.setObjects(List.of());
        listResources.add(certificateResourceRequest);

        //Set permissions to the role dto
        roleRequest.setResources(listResources);

        // Update Permissions using API Client
        roleManagementApiClient.savePermissions(roleUuid, roleRequest);
    }

    private void assignRoles(String userUuid, String roleUUid) {
        userManagementApiClient.updateRole(userUuid, roleUUid);
    }

    private void updateRolePermissions(Context context, Map<String, String> clientRoleMap) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT a.ra_profile_uuid, a.client_uuid, r.authority_instance_ref_uuid FROM client_authorization a JOIN ra_profile r ON a.ra_profile_uuid = r.uuid;")) {
                Map<String, Set<String>> raProfileMap = new HashMap<>();
                Map<String, Set<String>> authorityMap = new HashMap<>();
                while (rows.next()) {
                    String clientUuid = rows.getString("client_uuid");
                    String raProfileUuid = rows.getString("ra_profile_uuid");
                    String authorityUuid = rows.getString("authority_instance_ref_uuid");
                    raProfileMap.computeIfAbsent(clientUuid, k -> new HashSet<>()).add(raProfileUuid);
                    if (authorityUuid != null) {
                        authorityMap.computeIfAbsent(clientUuid, k -> new HashSet<>()).add(authorityUuid);
                    }
                }
                for (Map.Entry<String, String> entry : clientRoleMap.entrySet()) {
                    assignClientPermission(entry.getValue(), raProfileMap.getOrDefault(entry.getKey(), Set.of()), authorityMap.getOrDefault(entry.getKey(), Set.of()));
                }
            }
        }
    }

    private void performCleanups(Statement select) throws SQLException {
        List<String> cleanupCommands = new ArrayList<>();
        cleanupCommands.add("drop table client_authorization");
        cleanupCommands.add("drop table admin");
        cleanupCommands.add("drop table client");
        executeCommands(select, cleanupCommands);
    }

    private void executeCommands(Statement select, List<String> commands) throws SQLException {
        for (String command : commands) {
            select.execute(command);
        }
    }
}