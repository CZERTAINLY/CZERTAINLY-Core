package db.migration;

import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.DatabaseMigration;
import com.google.gson.Gson;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Migration script for the Json array migration to separate table
 */
public class V202303160830__AttributeContentJsonMigration extends BaseJavaMigration {

    private static final Logger logger = LoggerFactory.getLogger(V202303160830__AttributeContentJsonMigration.class);

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202303160830__JsonMigration.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            migrateJsonArraysToSingleRecords(context);
        }
    }

    private void migrateJsonArraysToSingleRecords(Context context) throws Exception {
        final Gson gson = new Gson();
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT ac.uuid, ac.attribute_content FROM attribute_content ac")) {
                List<String> commands = new ArrayList<>();
                while (rows.next()) {
                    final UUID attributeContentUUID = UUID.fromString(rows.getString("uuid"));
                    final String jsonArray = rows.getString("attribute_content");
                    final List<BaseAttributeContent> jsons = parseJsons(jsonArray);

                    final String insertScript = "INSERT INTO attribute_content_item (uuid, attribute_content_uuid, json) VALUES ('%s', '%s', '%s');";
                    jsons.forEach(json -> commands.add(String.format(insertScript, UUID.randomUUID(), attributeContentUUID, gson.toJson(json))));
                }
                executeCommands(select, commands);
            }
        }
    }

    private List<BaseAttributeContent> parseJsons(final String jsonArray) {
        return AttributeDefinitionUtils.deserializeAttributeContent(jsonArray, BaseAttributeContent.class);
    }

    private void executeCommands(Statement select, List<String> commands) throws SQLException {
        for (final String command : commands) {
            logger.info(command);
            select.execute(command);
        }
    }
}