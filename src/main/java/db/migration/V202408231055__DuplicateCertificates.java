package db.migration;


import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class V202408231055__DuplicateCertificates extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        mergeDuplicateCertificates(context);
//        addUniqueFingerprintConstraint(context);
    }

    private void addUniqueFingerprintConstraint(Context context) throws SQLException {
        String uniqueFingerprintConstraint = "ALTER TABLE certificate ADD UNIQUE (fingerprint);";
        try (final Statement statement = context.getConnection().createStatement()) {
            statement.execute(uniqueFingerprintConstraint);
        }

    }

    private void mergeDuplicateCertificates(Context context) throws SQLException {
        Statement select = context.getConnection().createStatement();
        ResultSet duplicateCertificatesGrouped = select.executeQuery("SELECT STRING_AGG(quote_literal(uuid::text), ',') AS uuids FROM certificate GROUP BY fingerprint HAVING COUNT(uuid) > 1;");
        List<String> commands = new ArrayList<>();
        while (duplicateCertificatesGrouped.next()) {
            String duplicateCertificatesUuids = duplicateCertificatesGrouped.getString("uuids");
            List<String> uuidsOfDuplicates = List.of(duplicateCertificatesUuids.split(","));
            String certificateToKeepUuid = uuidsOfDuplicates.getFirst();
            // Merge groups
            ResultSet groups = context.getConnection().createStatement().executeQuery("SELECT group_uuid FROM group_association WHERE resource = 'CERTIFICATE' AND object_uuid in (" + duplicateCertificatesUuids + ") GROUP BY group_uuid;");
            ResultSet certificateToKeepGroups = context.getConnection().createStatement().executeQuery("SELECT group_uuid FROM group_association WHERE resource = 'CERTIFICATE' AND object_uuid = " + certificateToKeepUuid + ";");
            Set<String> certificateToKeepGroupSet = new HashSet<>();
            while (certificateToKeepGroups.next()) {
                certificateToKeepGroupSet.add(certificateToKeepGroups.getString("group_uuid"));
            }
            while (groups.next()) {
                if (!certificateToKeepGroupSet.contains(groups.getString("group_uuid"))) {
                    commands.add("INSERT INTO group_association (uuid, resource, object_uuid, group_uuid) VALUES (gen_random_uuid(), 'CERTIFICATE'," + certificateToKeepUuid + ",'" + groups.getString("group_uuid") + "');");
                }
            }

            // Delete old group associations
            commands.add("DELETE FROM group_association WHERE resource = 'CERTIFICATE' AND object_uuid in (" + duplicateCertificatesUuids + ") AND object_uuid != " + certificateToKeepUuid + ";");
            // Find first certificate with owner and set that owner for certificate being kept
            ResultSet owners = context.getConnection().createStatement().executeQuery("SELECT owner_uuid, owner_username, object_uuid FROM owner_association WHERE resource = 'CERTIFICATE' AND object_uuid in (" + duplicateCertificatesUuids + ");");
            if (owners.next() && !Objects.equals(owners.getString("object_uuid"), certificateToKeepUuid)) {
                commands.add("INSERT INTO owner_association (uuid, resource, object_uuid, owner_uuid, owner_username) VALUES (gen_random_uuid(), 'CERTIFICATE'," + certificateToKeepUuid + ",'" + owners.getString("owner_uuid") + "', '" + owners.getString("owner_username") + "');");
            }
            // Delete old owner associations
            commands.add("DELETE FROM owner_association WHERE resource = 'CERTIFICATE' AND object_uuid in (" + duplicateCertificatesUuids + ") AND object_uuid != " + certificateToKeepUuid + ";");
            // Find first certificate with RA Profile and set that RA Profile for certificate being kept
            ResultSet raProfiles = context.getConnection().createStatement().executeQuery("SELECT ra_profile_uuid, uuid FROM certificate WHERE uuid in (" + duplicateCertificatesUuids + ");");
            if (owners.next() && !Objects.equals(raProfiles.getString("uuid"), certificateToKeepUuid)) {
                commands.add("UPDATE certificate SET ra_profile_uuid = '" + raProfiles.getString("ra_profile_uuid") + "WHERE uuid = " + certificateToKeepUuid + ";");
            }

            // Merge custom attributes

            ResultSet attributeContentItems = context.getConnection().createStatement().executeQuery("SELECT ac2o.uuid, object_uuid, attribute_content_item_uuid, aci.attribute_definition_uuid FROM attribute_content_2_object ac2o JOIN attribute_content_item aci ON aci.uuid = ac2o.attribute_content_item_uuid  WHERE ac2o.object_type = 'CERTIFICATE' AND ac2o.object_uuid in (" + duplicateCertificatesUuids + ");");
            ResultSet certificateToKeepAttributes = context.getConnection().createStatement().executeQuery("SELECT aci.attribute_definition_uuid FROM attribute_content_2_object ac2o JOIN attribute_content_item aci ON aci.uuid = ac2o.attribute_content_item_uuid  WHERE ac2o.object_type = 'CERTIFICATE' AND ac2o.object_uuid = " + certificateToKeepUuid + ";");
            Set<String> certificateToKeepAttributesSet = new HashSet<>();
            while (certificateToKeepAttributes.next()) {
                certificateToKeepAttributesSet.add(certificateToKeepAttributes.getString("attribute_definition_uuid"));
            }
            while (attributeContentItems.next()) {
                String attributeDefinitionUuid = attributeContentItems.getString("attribute_definition_uuid");
                if (!Objects.equals(attributeContentItems.getString("object_uuid"), certificateToKeepUuid) & !certificateToKeepAttributesSet.contains(attributeDefinitionUuid)) {
                    certificateToKeepAttributesSet.add(attributeDefinitionUuid);
                    commands.add("UPDATE attribute_content_2_object SET object_uuid = " + certificateToKeepUuid + " WHERE uuid = '" + attributeContentItems.getString("uuid") + "';");
                }
            }


        }

        for (String command : commands) {
            select.execute(command);
        }
    }


}
