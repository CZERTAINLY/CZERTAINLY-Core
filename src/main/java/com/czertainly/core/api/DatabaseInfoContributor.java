package com.czertainly.core.api;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Component
@Profile("!test")
public class DatabaseInfoContributor implements InfoContributor {

    private final DataSource dataSource;
    private final Map<String, Object> cachedDatabaseInfo;

    public DatabaseInfoContributor(DataSource dataSource) {
        this.dataSource = dataSource;
        this.cachedDatabaseInfo = precomputeDatabaseInfo();
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("db", cachedDatabaseInfo);
    }

    private Map<String, Object> precomputeDatabaseInfo() {
        Map<String, Object> dbInfo = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            String productName = metaData.getDatabaseProductName();
            String productVersion = metaData.getDatabaseProductVersion();

            dbInfo.put("system", productName != null ? productName : "Unknown");
            dbInfo.put("version", productVersion != null ? productVersion : "Unknown");
        } catch (SQLException e) {
            throw new RuntimeException("Unable to retrieve database information.", e);
        }

        return dbInfo;
    }
}