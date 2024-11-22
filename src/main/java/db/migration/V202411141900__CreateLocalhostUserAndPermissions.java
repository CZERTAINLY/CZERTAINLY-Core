package db.migration;

import com.czertainly.api.model.core.auth.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.DatabaseAuthMigration;
import com.czertainly.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.*;

/**
 * Migration script for the access control changes related to Localhost system user.
 * The script will take the data from the core database and trigger API calls to the
 * Auth Service for creating localhost role, user and the permissions.
 */
public class V202411141900__CreateLocalhostUserAndPermissions extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202411141900__CreateLocalhostUserAndPermissions.getChecksum();
    }

    public void migrate(Context context) throws Exception {
        // create role
        Map<Resource, List<ResourceAction>> roleResourceActions = new EnumMap<>(Resource.class);
        roleResourceActions.put(Resource.CERTIFICATE, List.of(ResourceAction.CREATE));
        roleResourceActions.put(Resource.USER, List.of(ResourceAction.CREATE, ResourceAction.UPDATE));
        roleResourceActions.put(Resource.SETTINGS, List.of(ResourceAction.LIST, ResourceAction.DETAIL, ResourceAction.UPDATE));

        RoleRequestDto roleRequestDto = new RoleRequestDto();
        roleRequestDto.setName(AuthHelper.LOCALHOST_USERNAME);
        roleRequestDto.setDescription("System role with all permissions needed for localhost operations");
        roleRequestDto.setSystemRole(true);
        RoleDetailDto localhostRole = DatabaseAuthMigration.createRole(roleRequestDto, roleResourceActions);

        // create user
        UserRequestDto userRequestDto = new UserRequestDto();
        userRequestDto.setUsername(AuthHelper.LOCALHOST_USERNAME);
        userRequestDto.setDescription("System user for localhost operations");
        userRequestDto.setEnabled(true);
        userRequestDto.setSystemUser(true);

        List<String> roleUuids = new ArrayList<>();
        roleUuids.add(localhostRole.getUuid());
        DatabaseAuthMigration.createUser(userRequestDto, roleUuids);
    }
}