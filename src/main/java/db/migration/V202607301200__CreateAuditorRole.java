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
 * Creates the {@code auditor} system role: every read action on every resource, and no action that changes anything.
 * It exists so that oversight - auditors, security reviewers, support - can be granted with one role instead of a
 * hand-assembled permission set that drifts behind the platform.
 * <p>
 * <b>No system user:</b> unlike acme/scep/cmp/localhost, this role is not an identity the platform assumes. It is
 * meant to be assigned to people, which is also why {@code RoleAssignmentGuard} keeps system roles without a system
 * user assignable to operators.
 * <p>
 * <b>Seeded, not authored:</b> the grants are derived from the catalogue the auth service already holds, so nothing
 * here has to be revisited when a resource or action is added, and nothing is granted that the auth service would
 * reject as unknown. On an upgrade that catalogue is complete and the role is usable immediately; on a fresh install
 * it is still sparse, and the role starts out granting little. Either way {@code AuthResourceSynchronizer} rederives
 * the role from the freshly scanned catalogue at the end of this same startup and every one after it.
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
            // Refusing to start over a name collision would be a harsh upgrade for a deployment that simply has a
            // role of its own called auditor. Leaving that role alone costs it the built-in one - the startup
            // reconciliation only touches system roles - and says so on every boot until the name is freed.
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
