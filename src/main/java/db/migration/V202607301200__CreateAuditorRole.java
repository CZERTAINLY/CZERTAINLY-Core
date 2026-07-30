package db.migration;

import com.otilm.api.model.core.auth.RolePermissionsRequestDto;
import com.otilm.api.model.core.auth.RoleRequestDto;
import com.otilm.core.auth.ReadOnlyRolePermissions;
import com.otilm.core.util.AuthHelper;
import com.otilm.core.util.DatabaseAuthMigration;
import com.otilm.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the {@code auditor} system role: every read action on every resource and nothing that changes anything.
 * It carries no system user, unlike acme/scep/cmp/localhost, because it is meant to be assigned to people.
 * <p>
 * The grants are derived from the catalogue the auth service already holds, so nothing is granted that it would
 * reject as unknown. On a fresh install that catalogue is still sparse and the role starts narrow;
 * {@code AuthResourceSynchronizer} rederives it later in this same startup and every one after.
 */
// Flyway mandates the V<version>__<Description> class-name format, which cannot match Sonar's S101 identifier pattern.
@SuppressWarnings("java:S101")
public class V202607301200__CreateAuditorRole extends BaseJavaMigration {

    private static final Logger logger = LoggerFactory.getLogger(V202607301200__CreateAuditorRole.class);

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202607301200__CreateAuditorRole.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {
        if (DatabaseAuthMigration.getRoleNames().contains(AuthHelper.AUDITOR_ROLE_NAME)) {
            // Refusing to start over a name collision would be harsh on a deployment that has its own auditor role.
            // Leaving it alone costs that deployment the built-in one, and says so on every boot.
            logger.warn("A role named '{}' already exists and was left untouched, so the read-only system role was"
                    + " not created. Rename the existing role to have the platform manage '{}'.",
                    AuthHelper.AUDITOR_ROLE_NAME, AuthHelper.AUDITOR_ROLE_NAME);
            return;
        }

        RolePermissionsRequestDto readEverything = ReadOnlyRolePermissions.deriveFromAuthResources(
                DatabaseAuthMigration.getResourceApiClient().getAuthResources());

        RoleRequestDto roleRequestDto = new RoleRequestDto();
        roleRequestDto.setName(AuthHelper.AUDITOR_ROLE_NAME);
        roleRequestDto.setDescription("System role granting every read action on every resource and nothing that"
                + " changes anything; kept in step with the resource catalogue on every startup");
        roleRequestDto.setSystemRole(true);
        DatabaseAuthMigration.createRole(roleRequestDto, readEverything);
    }
}
