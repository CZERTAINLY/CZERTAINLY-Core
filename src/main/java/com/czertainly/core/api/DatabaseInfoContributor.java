package com.czertainly.core.api;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

@Component
@Profile("!test")
public class DatabaseInfoContributor implements InfoContributor {

    private final Map<String, Object> cachedDatabaseInfo = new HashMap<>();

    public DatabaseInfoContributor() {
        precomputeDatabaseInfo();
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("db", cachedDatabaseInfo);
    }

    private void precomputeDatabaseInfo() {
        String url = System.getenv("JDBC_URL");
        String username = System.getenv("JDBC_USERNAME");
        String password = System.getenv("JDBC_PASSWORD");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            DatabaseMetaData metaData = connection.getMetaData();
            cachedDatabaseInfo.put("system", metaData.getDatabaseProductName());
            cachedDatabaseInfo.put("version", metaData.getDatabaseProductVersion());
        } catch (Exception e) {
            throw new RuntimeException("Unable to retrieve database information.", e);
        }
    }
}
