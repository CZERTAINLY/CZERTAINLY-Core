package db.migration;

import com.czertainly.api.clients.BaseApiClient;
import com.czertainly.api.clients.cryptography.KeyManagementApiClient;
import com.czertainly.api.clients.cryptography.TokenInstanceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.cryptography.key.CreateKeyRequestDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.config.TrustedCertificatesConfig;
import com.czertainly.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

@SuppressWarnings("java:S101")
public class V202507051200__DeactivateTokenWithDeprecatedKeyAlgorithms extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202507051200__DeactivateTokenWithDeprecatedKeyAlgorithms.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {
        TrustedCertificatesConfig trustedCertificatesConfig = new TrustedCertificatesConfig();
        trustedCertificatesConfig.configureGlobalTrustStore();
        WebClient webClient = BaseApiClient.prepareWebClient();
        KeyManagementApiClient keyManagementApiClient = new KeyManagementApiClient(webClient, trustedCertificatesConfig.getDefaultTrustManagers());
        TokenInstanceApiClient tokenInstanceApiClient = new TokenInstanceApiClient(webClient, trustedCertificatesConfig.getDefaultTrustManagers());

        try (final Statement select = context.getConnection().createStatement()) {
            ResultSet tokens = select.executeQuery("SELECT uuid, token_instance_uuid, connector_uuid, status FROM token_instance_reference;");
            String deactivateTokenQuery = "UPDATE token_instance_reference SET status = 'DEACTIVATED' WHERE uuid = ?;";
            try (PreparedStatement deactivateTokenStatement = context.getConnection().prepareStatement(deactivateTokenQuery)) {
                while (tokens.next()) {
                    if (tokens.getString("status").equals("ACTIVATED")) {
                        try (final Statement selectConnector = context.getConnection().createStatement()) {
                            ResultSet connector = selectConnector.executeQuery("SELECT url, status FROM connector WHERE uuid = '" + tokens.getString("connector_uuid") + "';");
                            connector.next();
                            String tokenInstanceUuid = tokens.getString("token_instance_uuid");
                            ConnectorDto connectorDto = new ConnectorDto();
                            connectorDto.setUrl(connector.getString("url"));
                            connectorDto.setStatus(ConnectorStatus.valueOf(connector.getString("status")));
                            try {
                                keyManagementApiClient.createKeyPair(connectorDto, tokenInstanceUuid, new CreateKeyRequestDto());
                            } catch (ValidationException e) {
                                deactivateTokenStatement.setObject(1, tokens.getString("uuid"), Types.OTHER);
                                deactivateTokenStatement.addBatch();
                                tokenInstanceApiClient.deactivateTokenInstance(connectorDto, tokenInstanceUuid);
                            } catch (ConnectorException ignored) {
                                // Exception ignored otherwise
                            }
                        }
                    }
                    }
                    deactivateTokenStatement.executeBatch();
                }
            }
        }
    }
