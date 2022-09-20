package db.migration;

import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.RoleRequestDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
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
import java.util.List;
import java.util.Properties;

/**
 * Migration script for the Access control changes.
 * The script will take the data from the core database and trigger API calls to the
 * Auth Service for creating, users, roles, RA Profile Authorizations. Once the migrations are completed,
 * then the data in the core will be cleaned up.
 */
@PropertySource("classpath:application.properties")
public class V202209161100__Access_Control extends BaseJavaMigration {

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

    public void migrate(Context context) throws Exception {
        userManagementApiClient = new UserManagementApiClient(getAuthServiceUrl(), client);
        roleManagementApiClient = new RoleManagementApiClient(getAuthServiceUrl(), client);
        try (Statement select = context.getConnection().createStatement()) {
            List<String> adminNames = createAdministratorUsers(context);
            createClientUsers(context, adminNames);
        }
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
                    assignPermissions(response.getUuid(), rows.getString("role").equals("SUPERADMINISTRATOR") ? AUTH_SUPER_ADMIN_ROLE_UUID : AUTH_ADMIN_ROLE_UUID);
                    adminNames.add(dto.getUsername());
                }
                return adminNames;
            }
        }
    }

    private void createClientUsers(Context context, List<String> adminNames) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT a.uuid, a.name, a.certificate_uuid, a.enabled, c.fingerprint FROM client a JOIN certificate c ON a.certificate_uuid = c.uuid WHERE a.certificate_uuid NOT IN (SELECT b.certificate_uuid FROM admin b);")) {
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
                    createClientRoles(response.getUuid(), clientName);
                }
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

    private void createClientRoles(String clientUuid, String clientName) {
        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Client_Role_" + clientName);
        dto.setDescription("Role created during migration for the client " + clientName);
        RoleDto response = roleManagementApiClient.createRole(dto);
        assignPermissions(clientUuid, response.getUuid());
    }

    private void assignPermissions(String userUuid, String roleUUid) {
        userManagementApiClient.updateRole(userUuid, roleUUid);
    }

    private void executeCommands(Statement select, List<String> commands) throws SQLException {
        for (String command : commands) {
            select.execute(command);
        }
    }
}