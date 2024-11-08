package com.czertainly.core.auth.oauth2;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * This is a copy of class {@link JdbcOAuth2AuthorizedClientService}, modified to be able to change table name.
 */
public class CzertainlyJdbcOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    // @formatter:off
    private static final String COLUMN_NAMES = "client_registration_id, "
            + "principal_name, "
            + "access_token_type, "
            + "access_token_value, "
            + "access_token_issued_at, "
            + "access_token_expires_at, "
            + "access_token_scopes, "
            + "refresh_token_value, "
            + "refresh_token_issued_at";
    // @formatter:on

    @Value("${DB_SCHEMA:core}")
    private String schema;

    private static final String PK_FILTER = "client_registration_id = ? AND principal_name = ?";

    private String loadAuthorizedClientSql;

    private String saveAuthorizedClientSql;

    private String removeAuthorizedClientSql;

    private String updateAuthorizedClientSql;

    protected final JdbcOperations jdbcOperations;

    protected RowMapper<OAuth2AuthorizedClient> authorizedClientRowMapper;

    protected Function<OAuth2AuthorizedClientHolder, List<SqlParameterValue>> authorizedClientParametersMapper;

    protected final LobHandler lobHandler;

    @PostConstruct
    public void initializeConstants() {
        String tableName = schema + ".oauth2_authorized_client";
        loadAuthorizedClientSql = "SELECT " + COLUMN_NAMES
                + " FROM " + tableName
                + " WHERE " + PK_FILTER;
        saveAuthorizedClientSql = "INSERT INTO " + tableName
                + " (" + COLUMN_NAMES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        removeAuthorizedClientSql = "DELETE FROM " + tableName + " WHERE " + PK_FILTER;
        updateAuthorizedClientSql = "UPDATE " + tableName
                + " SET access_token_type = ?, access_token_value = ?, access_token_issued_at = ?,"
                + " access_token_expires_at = ?, access_token_scopes = ?,"
                + " refresh_token_value = ?, refresh_token_issued_at = ?"
                + " WHERE " + PK_FILTER;
    }

    /**
     * Constructs a {@code JdbcOAuth2AuthorizedClientService} using the provided
     * parameters.
     *
     * @param jdbcOperations               the JDBC operations
     * @param clientRegistrationRepository the repository of client registrations
     */
    public CzertainlyJdbcOAuth2AuthorizedClientService(JdbcOperations jdbcOperations,
                                                       ClientRegistrationRepository clientRegistrationRepository) {
        this(jdbcOperations, clientRegistrationRepository, new DefaultLobHandler());
    }

    /**
     * Constructs a {@code JdbcOAuth2AuthorizedClientService} using the provided
     * parameters.
     *
     * @param jdbcOperations               the JDBC operations
     * @param clientRegistrationRepository the repository of client registrations
     * @param lobHandler                   the handler for large binary fields and large text fields
     * @since 5.5
     */
    public CzertainlyJdbcOAuth2AuthorizedClientService(JdbcOperations jdbcOperations,
                                                       ClientRegistrationRepository clientRegistrationRepository, LobHandler lobHandler) {
        Assert.notNull(jdbcOperations, "jdbcOperations cannot be null");
        Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
        Assert.notNull(lobHandler, "lobHandler cannot be null");
        this.jdbcOperations = jdbcOperations;
        this.lobHandler = lobHandler;
        OAuth2AuthorizedClientRowMapper clientRowMapper = new OAuth2AuthorizedClientRowMapper(
                clientRegistrationRepository);
        clientRowMapper.setLobHandler(lobHandler);
        this.authorizedClientRowMapper = clientRowMapper;
        this.authorizedClientParametersMapper = new OAuth2AuthorizedClientParametersMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
                                                                     String principalName) {
        Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");
        SqlParameterValue[] parameters = new SqlParameterValue[]{
                new SqlParameterValue(Types.VARCHAR, clientRegistrationId),
                new SqlParameterValue(Types.VARCHAR, principalName)};
        PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters);
        List<OAuth2AuthorizedClient> result = this.jdbcOperations.query(loadAuthorizedClientSql, pss,
                this.authorizedClientRowMapper);
        return !result.isEmpty() ? (T) result.get(0) : null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        Assert.notNull(authorizedClient, "authorizedClient cannot be null");
        Assert.notNull(principal, "principal cannot be null");
        boolean existsAuthorizedClient = null != this
                .loadAuthorizedClient(authorizedClient.getClientRegistration().getRegistrationId(), principal.getName());
        if (existsAuthorizedClient) {
            updateAuthorizedClient(authorizedClient, principal);
        } else {
            try {
                insertAuthorizedClient(authorizedClient, principal);
            } catch (DuplicateKeyException ex) {
                updateAuthorizedClient(authorizedClient, principal);
            }
        }
    }

    private void updateAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        List<SqlParameterValue> parameters = this.authorizedClientParametersMapper
                .apply(new OAuth2AuthorizedClientHolder(authorizedClient, principal));
        SqlParameterValue clientRegistrationIdParameter = parameters.remove(0);
        SqlParameterValue principalNameParameter = parameters.remove(0);
        parameters.add(clientRegistrationIdParameter);
        parameters.add(principalNameParameter);
        try (LobCreator lobCreator = this.lobHandler.getLobCreator()) {
            PreparedStatementSetter pss = new LobCreatorArgumentPreparedStatementSetter(lobCreator,
                    parameters.toArray());
            this.jdbcOperations.update(updateAuthorizedClientSql, pss);
        }
    }

    private void insertAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        List<SqlParameterValue> parameters = this.authorizedClientParametersMapper
                .apply(new OAuth2AuthorizedClientHolder(authorizedClient, principal));
        try (LobCreator lobCreator = this.lobHandler.getLobCreator()) {
            PreparedStatementSetter pss = new LobCreatorArgumentPreparedStatementSetter(lobCreator,
                    parameters.toArray());
            this.jdbcOperations.update(saveAuthorizedClientSql, pss);
        }
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");
        SqlParameterValue[] parameters = new SqlParameterValue[]{
                new SqlParameterValue(Types.VARCHAR, clientRegistrationId),
                new SqlParameterValue(Types.VARCHAR, principalName)};
        PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters);
        this.jdbcOperations.update(removeAuthorizedClientSql, pss);
    }

    /**
     * Sets the {@link RowMapper} used for mapping the current row in
     * {@code java.sql.ResultSet} to {@link OAuth2AuthorizedClient}. The default is
     * {@link OAuth2AuthorizedClientRowMapper}.
     *
     * @param authorizedClientRowMapper the {@link RowMapper} used for mapping the current
     *                                  row in {@code java.sql.ResultSet} to {@link OAuth2AuthorizedClient}
     */
    public final void setAuthorizedClientRowMapper(RowMapper<OAuth2AuthorizedClient> authorizedClientRowMapper) {
        Assert.notNull(authorizedClientRowMapper, "authorizedClientRowMapper cannot be null");
        this.authorizedClientRowMapper = authorizedClientRowMapper;
    }

    /**
     * Sets the {@code Function} used for mapping {@link OAuth2AuthorizedClientHolder} to
     * a {@code List} of {@link SqlParameterValue}. The default is
     * {@link OAuth2AuthorizedClientParametersMapper}.
     *
     * @param authorizedClientParametersMapper the {@code Function} used for mapping
     *                                         {@link OAuth2AuthorizedClientHolder} to a {@code List} of {@link SqlParameterValue}
     */
    public final void setAuthorizedClientParametersMapper(
            Function<OAuth2AuthorizedClientHolder, List<SqlParameterValue>> authorizedClientParametersMapper) {
        Assert.notNull(authorizedClientParametersMapper, "authorizedClientParametersMapper cannot be null");
        this.authorizedClientParametersMapper = authorizedClientParametersMapper;
    }

    /**
     * The default {@link RowMapper} that maps the current row in
     * {@code java.sql.ResultSet} to {@link OAuth2AuthorizedClient}.
     */
    public static class OAuth2AuthorizedClientRowMapper implements RowMapper<OAuth2AuthorizedClient> {

        protected final ClientRegistrationRepository clientRegistrationRepository;

        protected LobHandler lobHandler = new DefaultLobHandler();

        public OAuth2AuthorizedClientRowMapper(ClientRegistrationRepository clientRegistrationRepository) {
            Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
            this.clientRegistrationRepository = clientRegistrationRepository;
        }

        public final void setLobHandler(LobHandler lobHandler) {
            Assert.notNull(lobHandler, "lobHandler cannot be null");
            this.lobHandler = lobHandler;
        }

        @Override
        public OAuth2AuthorizedClient mapRow(ResultSet rs, int rowNum) throws SQLException {
            String clientRegistrationId = rs.getString("client_registration_id");
            ClientRegistration clientRegistration = this.clientRegistrationRepository
                    .findByRegistrationId(clientRegistrationId);
            if (clientRegistration == null) {
                throw new DataRetrievalFailureException(
                        "The ClientRegistration with id '" + clientRegistrationId + "' exists in the data source, "
                                + "however, it was not found in the ClientRegistrationRepository.");
            }
            OAuth2AccessToken.TokenType tokenType = null;
            if (OAuth2AccessToken.TokenType.BEARER.getValue().equalsIgnoreCase(rs.getString("access_token_type"))) {
                tokenType = OAuth2AccessToken.TokenType.BEARER;
            }
            String tokenValue = new String(this.lobHandler.getBlobAsBytes(rs, "access_token_value"),
                    StandardCharsets.UTF_8);
            Instant issuedAt = rs.getTimestamp("access_token_issued_at").toInstant();
            Instant expiresAt = rs.getTimestamp("access_token_expires_at").toInstant();
            Set<String> scopes = Collections.emptySet();
            String accessTokenScopes = rs.getString("access_token_scopes");
            if (accessTokenScopes != null) {
                scopes = StringUtils.commaDelimitedListToSet(accessTokenScopes);
            }
            OAuth2AccessToken accessToken = new OAuth2AccessToken(tokenType, tokenValue, issuedAt, expiresAt, scopes);
            OAuth2RefreshToken refreshToken = null;
            byte[] refreshTokenValue = this.lobHandler.getBlobAsBytes(rs, "refresh_token_value");
            if (refreshTokenValue != null) {
                tokenValue = new String(refreshTokenValue, StandardCharsets.UTF_8);
                issuedAt = null;
                Timestamp refreshTokenIssuedAt = rs.getTimestamp("refresh_token_issued_at");
                if (refreshTokenIssuedAt != null) {
                    issuedAt = refreshTokenIssuedAt.toInstant();
                }
                refreshToken = new OAuth2RefreshToken(tokenValue, issuedAt);
            }
            String principalName = rs.getString("principal_name");
            return new OAuth2AuthorizedClient(clientRegistration, principalName, accessToken, refreshToken);
        }

    }

    /**
     * The default {@code Function} that maps {@link OAuth2AuthorizedClientHolder} to a
     * {@code List} of {@link SqlParameterValue}.
     */
    public static class OAuth2AuthorizedClientParametersMapper
            implements Function<OAuth2AuthorizedClientHolder, List<SqlParameterValue>> {

        @Override
        public List<SqlParameterValue> apply(OAuth2AuthorizedClientHolder authorizedClientHolder) {
            OAuth2AuthorizedClient authorizedClient = authorizedClientHolder.authorizedClient();
            Authentication principal = authorizedClientHolder.principal();
            ClientRegistration clientRegistration = authorizedClient.getClientRegistration();
            OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
            OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
            List<SqlParameterValue> parameters = new ArrayList<>();
            parameters.add(new SqlParameterValue(Types.VARCHAR, clientRegistration.getRegistrationId()));
            parameters.add(new SqlParameterValue(Types.VARCHAR, principal.getName()));
            parameters.add(new SqlParameterValue(Types.VARCHAR, accessToken.getTokenType().getValue()));
            parameters
                    .add(new SqlParameterValue(Types.BLOB, accessToken.getTokenValue().getBytes(StandardCharsets.UTF_8)));
            parameters.add(new SqlParameterValue(Types.TIMESTAMP, Timestamp.from(accessToken.getIssuedAt())));
            parameters.add(new SqlParameterValue(Types.TIMESTAMP, Timestamp.from(accessToken.getExpiresAt())));
            String accessTokenScopes = null;
            if (!CollectionUtils.isEmpty(accessToken.getScopes())) {
                accessTokenScopes = StringUtils.collectionToDelimitedString(accessToken.getScopes(), ",");
            }
            parameters.add(new SqlParameterValue(Types.VARCHAR, accessTokenScopes));
            byte[] refreshTokenValue = null;
            Timestamp refreshTokenIssuedAt = null;
            if (refreshToken != null) {
                refreshTokenValue = refreshToken.getTokenValue().getBytes(StandardCharsets.UTF_8);
                if (refreshToken.getIssuedAt() != null) {
                    refreshTokenIssuedAt = Timestamp.from(refreshToken.getIssuedAt());
                }
            }
            parameters.add(new SqlParameterValue(Types.BLOB, refreshTokenValue));
            parameters.add(new SqlParameterValue(Types.TIMESTAMP, refreshTokenIssuedAt));
            return parameters;
        }

    }

    /**
     * A holder for an {@link OAuth2AuthorizedClient} and End-User {@link Authentication}
     * (Resource Owner).
     */
    public record OAuth2AuthorizedClientHolder(OAuth2AuthorizedClient authorizedClient, Authentication principal) {

        /**
         * Constructs an {@code OAuth2AuthorizedClientHolder} using the provided
         * parameters.
         *
         * @param authorizedClient the authorized client
         * @param principal        the End-User {@link Authentication} (Resource Owner)
         */
        public OAuth2AuthorizedClientHolder {
            Assert.notNull(authorizedClient, "authorizedClient cannot be null");
            Assert.notNull(principal, "principal cannot be null");
        }
    }

    private static final class LobCreatorArgumentPreparedStatementSetter extends ArgumentPreparedStatementSetter {

        protected final LobCreator lobCreator;

        private LobCreatorArgumentPreparedStatementSetter(LobCreator lobCreator, Object[] args) {
            super(args);
            this.lobCreator = lobCreator;
        }

        @Override
        protected void doSetValue(PreparedStatement ps, int parameterPosition, Object argValue) throws SQLException {
            if (argValue instanceof SqlParameterValue paramValue && paramValue.getSqlType() == Types.BLOB) {
                if (paramValue.getValue() != null) {
                    Assert.isInstanceOf(byte[].class, paramValue.getValue(),
                            "Value of blob parameter must be byte[]");
                }
                byte[] valueBytes = (byte[]) paramValue.getValue();
                this.lobCreator.setBlobAsBytes(ps, parameterPosition, valueBytes);
                return;
            }
            super.doSetValue(ps, parameterPosition, argValue);
        }

    }

}
