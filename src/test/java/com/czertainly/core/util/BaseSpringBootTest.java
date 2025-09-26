package com.czertainly.core.util;

import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.api.model.core.logging.enums.AuthMethod;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.OpaObjectAccessResult;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Import(SpringBootTestContext.class)
public class BaseSpringBootTest {

    @Autowired
    OpaClient opaClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String truncateTablesSql;

    @BeforeEach
    public void setupAuth() throws SQLException {
        mockSuccessfulCheckResourceAccess();
        mockSuccessfulCheckObjectAccess();
        injectAuthentication();

        // clean DB tables data before each test
        truncateTables();
        // clean context
        MDC.clear();
    }

    private void truncateTables() throws SQLException {
        if (jdbcTemplate.getDataSource() == null) {
            throw new SQLException("JDBCTemplate does not have initialized data source");
        }

        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (truncateTablesSql == null) {
                var tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"});
                int counter = 0;
                StringBuilder stringBuilder = new StringBuilder("TRUNCATE ");
                while (tables.next()) {
                    String tableName = "\"%s\"".formatted(tables.getString("TABLE_NAME"));

                    if (counter == 0) {
                        stringBuilder.append(tableName);
                    } else {
                        stringBuilder.append(", ").append(tableName);
                    }
                    ++counter;
                }
                truncateTablesSql = stringBuilder.toString();
            }
            connection.prepareStatement(truncateTablesSql).execute();
        }
    }

    protected void mockSuccessfulCheckResourceAccess() {
        OpaResourceAccessResult accessAllowed = new OpaResourceAccessResult();
        accessAllowed.setAuthorized(true);
        accessAllowed.setAllow(List.of());

        Mockito.when(
                opaClient.checkResourceAccess(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())
        ).thenReturn(accessAllowed);
    }

    protected void mockSuccessfulCheckObjectAccess() {
        OpaObjectAccessResult objectAccessAllowed = new OpaObjectAccessResult();
        objectAccessAllowed.setActionAllowedForGroupOfObjects(true);
        objectAccessAllowed.setAllowedObjects(List.of());
        objectAccessAllowed.setForbiddenObjects(List.of());

        Mockito.when(
                opaClient.checkObjectAccess(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())
        ).thenReturn(objectAccessAllowed);
    }

    protected void injectAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(getAuthentication());
    }

    protected Authentication getAuthentication() {
        UserProfileDto userProfileDto = new UserProfileDto();
        UserDto userDto = new UserDto();
        userDto.setUuid(UUID.randomUUID().toString());
        userDto.setUsername("tst-user");
        userDto.setFirstName("Test");
        userDto.setLastName("Tester");
        userDto.setSystemUser(true);
        userProfileDto.setUser(userDto);

        String rawData;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            rawData = objectMapper.writeValueAsString(userProfileDto);
        } catch (JsonProcessingException e) {
            rawData = String.format("{\"user\":{\"uuid\":\"%s\", \"uuid\":\"%s\"}}", userDto.getUuid(), userDto.getUsername());
        }

        AuthenticationInfo info = new AuthenticationInfo(AuthMethod.USER_PROXY, userDto.getUuid(), userDto.getUsername(), List.of(), rawData);
        return new CzertainlyAuthenticationToken(new CzertainlyUserDetails(info));
    }
}
