package db.migration;

import com.czertainly.api.model.core.auth.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.DatabaseAuthMigration;
import com.czertainly.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.*;

/**
 * Migration script for the access control changes related to TSP system user.
 * Creates the TSP role and user (if not yet present) with all permissions needed for TSP operations,
 * or updates permissions on the existing role.
 * <p>
 * Permissions:
 * - TSP_PROFILE: DETAIL, LIST
 * - SIGNING_PROFILE: DETAIL
 * - DIGITAL_SIGNATURE: SIGN
 * - CERTIFICATE: DETAIL
 * - CRYPTOGRAPHIC_KEY: SIGN
 * - TOKEN: DETAIL
 * - TOKEN_PROFILE: DETAIL
 * - RA_PROFILE: MEMBERS
 */
public class V202604101200__CreateTspUserAndPermissions extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202604101200__CreateTspUserAndPermissions.getChecksum();
    }

    public void migrate(Context context) throws Exception {
        // seed resources
        Map<Resource, List<ResourceAction>> resources = new EnumMap<>(Resource.class);
        resources.put(Resource.CRYPTOGRAPHIC_KEY, List.of(ResourceAction.SIGN));
        resources.put(Resource.DIGITAL_SIGNATURE, List.of(ResourceAction.SIGN));
        resources.put(Resource.TOKEN, List.of(ResourceAction.DETAIL));
        resources.put(Resource.TOKEN_PROFILE, List.of(ResourceAction.DETAIL));
        resources.put(Resource.TSP_PROFILE, List.of(ResourceAction.DETAIL, ResourceAction.LIST));
        resources.put(Resource.SIGNING_PROFILE, List.of(ResourceAction.DETAIL));
        DatabaseAuthMigration.seedResources(resources);

        // all permissions needed for TSP operations
        Map<Resource, List<ResourceAction>> roleResourceActions = new EnumMap<>(Resource.class);
        roleResourceActions.put(Resource.TSP_PROFILE, List.of(ResourceAction.DETAIL, ResourceAction.LIST));
        roleResourceActions.put(Resource.SIGNING_PROFILE, List.of(ResourceAction.DETAIL));
        roleResourceActions.put(Resource.DIGITAL_SIGNATURE, List.of(ResourceAction.SIGN));
        roleResourceActions.put(Resource.CERTIFICATE, List.of(ResourceAction.DETAIL));
        roleResourceActions.put(Resource.CRYPTOGRAPHIC_KEY, List.of(ResourceAction.SIGN));
        roleResourceActions.put(Resource.TOKEN, List.of(ResourceAction.DETAIL));
        roleResourceActions.put(Resource.TOKEN_PROFILE, List.of(ResourceAction.DETAIL));
        roleResourceActions.put(Resource.RA_PROFILE, List.of(ResourceAction.MEMBERS));

        // check if TSP role already exists
        Map<String, String> rolesMapping = DatabaseAuthMigration.getSystemRolesMapping();
        String existingRoleUuid = rolesMapping.get(AuthHelper.TSP_USERNAME);

        if (existingRoleUuid != null) {
            // role already exists, update permissions
            DatabaseAuthMigration.updateRolePermissions(existingRoleUuid, roleResourceActions);
        } else {
            // create role
            RoleRequestDto roleRequestDto = new RoleRequestDto();
            roleRequestDto.setName(AuthHelper.TSP_USERNAME);
            roleRequestDto.setDescription("System role with all permissions needed for TSP operations");
            roleRequestDto.setSystemRole(true);
            RoleDetailDto tspRole = DatabaseAuthMigration.createRole(roleRequestDto, roleResourceActions);

            // create user
            UserRequestDto userRequestDto = new UserRequestDto();
            userRequestDto.setUsername(AuthHelper.TSP_USERNAME);
            userRequestDto.setDescription("System user for TSP operations");
            userRequestDto.setEnabled(true);
            userRequestDto.setSystemUser(true);

            List<String> roleUuids = new ArrayList<>();
            roleUuids.add(tspRole.getUuid());
            DatabaseAuthMigration.createUser(userRequestDto, roleUuids);
        }
    }
}
