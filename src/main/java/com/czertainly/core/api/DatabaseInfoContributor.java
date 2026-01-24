package com.czertainly.core.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("!test")
public class DatabaseInfoContributor implements InfoContributor {

    private final Map<String, Object> cachedDatabaseInfo = new HashMap<>();

    private final String jdbcUrl;
    private final String jdbcUsername;
    private final String jdbcPassword;

    public DatabaseInfoContributor(@Value("${spring.datasource.url}") String jdbcUrl,
                                   @Value("${spring.datasource.username}") String jdbcUsername,
                                   @Value("${spring.datasource.password}") String  jdbcPassword) {
        this.jdbcUrl = jdbcUrl;
        this.jdbcUsername = jdbcUsername;
        this.jdbcPassword = jdbcPassword;
        precomputeDatabaseInfo();
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("db", cachedDatabaseInfo);
    }

    private void precomputeDatabaseInfo() {
        String url = Optional.ofNullable(System.getenv("JDBC_URL")).orElse(jdbcUrl);
        String username = Optional.ofNullable(System.getenv("JDBC_USERNAME")).orElse(jdbcUsername);
        String password = Optional.ofNullable(System.getenv("JDBC_PASSWORD")).orElse(jdbcPassword);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            DatabaseMetaData metaData = connection.getMetaData();
            cachedDatabaseInfo.put("system", metaData.getDatabaseProductName());
            cachedDatabaseInfo.put("version", metaData.getDatabaseProductVersion());
        } catch (Exception e) {
            throw new RuntimeException("Unable to retrieve database information.", e);
        }
    }
}
