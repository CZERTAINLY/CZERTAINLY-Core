package db.migration;

import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

    private static final String CREDENTIAL_TABLE_NAME = "credential";

    private WebClient client;
    private UserManagementApiClient userManagementApiClient;

    private static Properties properties;

    @Override
    public Integer getChecksum() {
        return 123456;
    }

    public void migrate(Context context) throws Exception {
        userManagementApiClient = new UserManagementApiClient(getAuthServiceUrl(), client);
        try (Statement select = context.getConnection().createStatement()) {
            createUsers(context);
        }
    }

    private void createUsers(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT a.username, a.name, a.surname, a.email, a.certificate_uuid, a.enabled, c.fingerprint FROM admin a JOIN certificate c ON a.certificate_uuid = c.uuid;")) {
                while (rows.next()) {
                    UserRequestDto dto = new UserRequestDto();
                    String certificateUuid = rows.getString("certificate_uuid");
                    dto.setCertificateUuid(certificateUuid);
                    dto.setLastName(rows.getString("surname"));
                    dto.setFirstName(rows.getString("name"));
                    dto.setUsername(rows.getString("username"));
                    dto.setEmail(rows.getString("email"));
                    Boolean enabled = rows.getBoolean("enabled");
                    if (enabled == null) {
                        enabled = false;
                    }
                    dto.setEnabled(enabled);
                    dto.setCertificateFingerprint(rows.getString("fingerprint"));
                    UserDto user = userManagementApiClient.createUser(dto);
                    System.out.println(user.toString());
                }
            }
        }
    }

    private String getAuthServiceUrl() throws IOException, URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource("application.properties");
        File file = new File(resource.toURI());

        InputStream targetStream = new FileInputStream(file);
        Properties properties = new Properties();
        properties.load(targetStream);
        return (String) properties.get("auth-service.base-url");
    }


    private void executeCommands(Statement select, List<String> commands) throws SQLException {
        for (String command : commands) {
            select.execute(command);
        }
    }
}