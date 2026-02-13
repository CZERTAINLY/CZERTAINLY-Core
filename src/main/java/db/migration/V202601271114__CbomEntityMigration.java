package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V202601271114__CbomEntityMigration extends BaseJavaMigration {

    private static final Logger logger = LoggerFactory.getLogger(V202601271114__CbomEntityMigration.class);

    private static final String CREATE_CBOM_ENTITY_TABLE = """
    CREATE TABLE cbom (
        uuid UUID PRIMARY KEY DEFAULT,
        serial_number TEXT NOT NULL,
        version INT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        cbom_timestamp TIMESTAMPTZ NOT NULL,
        source TEXT,
        algorithms_count INT NOT NULL,
        certificates_count INT NOT NULL,
        protocols_count INT NOT NULL,
        crypto_material_count INT NOT NULL,
        total_assets_count INT NOT NULL
    );
    """;

    @Override
    public void migrate(final Context context) throws Exception {
        try (final Statement statement = context.getConnection().createStatement()) {
            statement.execute(CREATE_CBOM_ENTITY_TABLE);
            logger.info("Created cbom table");
        }
    }
}
