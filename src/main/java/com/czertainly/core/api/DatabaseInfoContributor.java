package com.czertainly.core.api;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

@Component
public class DatabaseInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        String url = System.getenv("JDBC_URL");
        String username = System.getenv("JDBC_USERNAME");
        String password = System.getenv("JDBC_PASSWORD");
        try {
            Connection connection = DriverManager.getConnection(url, username, password);
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, String> databaseDetails = new HashMap<>();
            databaseDetails.put("system", metaData.getDatabaseProductName());
            databaseDetails.put("version", metaData.getDatabaseProductVersion());
            builder.withDetail("db", databaseDetails);
        } catch (Exception e) {
            throw new RuntimeException("Unable to retrieve database information.", e);
        }
    }

}