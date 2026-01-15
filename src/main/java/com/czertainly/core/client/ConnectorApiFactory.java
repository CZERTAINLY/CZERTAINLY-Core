package com.czertainly.core.client;

import com.czertainly.api.clients.AttributeApiClient;
import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.clients.CertificateApiClient;
import com.czertainly.api.clients.ComplianceApiClient;
import com.czertainly.api.clients.ConnectorApiClient;
import com.czertainly.api.clients.DiscoveryApiClient;
import com.czertainly.api.clients.EndEntityApiClient;
import com.czertainly.api.clients.EndEntityProfileApiClient;
import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.clients.HealthApiClient;
import com.czertainly.api.clients.LocationApiClient;
import com.czertainly.api.clients.NotificationInstanceApiClient;
import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.clients.cryptography.KeyManagementApiClient;
import com.czertainly.api.clients.cryptography.TokenInstanceApiClient;
import com.czertainly.api.interfaces.client.AttributeSyncApiClient;
import com.czertainly.api.interfaces.client.AuthorityInstanceSyncApiClient;
import com.czertainly.api.interfaces.client.CertificateSyncApiClient;
import com.czertainly.api.interfaces.client.CertificateSyncApiClientV2;
import com.czertainly.api.interfaces.client.ComplianceSyncApiClient;
import com.czertainly.api.interfaces.client.ComplianceSyncApiClientV2;
import com.czertainly.api.interfaces.client.ConnectorSyncApiClient;
import com.czertainly.api.interfaces.client.CryptographicOperationsSyncApiClient;
import com.czertainly.api.interfaces.client.DiscoverySyncApiClient;
import com.czertainly.api.interfaces.client.EndEntityProfileSyncApiClient;
import com.czertainly.api.interfaces.client.EndEntitySyncApiClient;
import com.czertainly.api.interfaces.client.EntityInstanceSyncApiClient;
import com.czertainly.api.interfaces.client.HealthSyncApiClient;
import com.czertainly.api.interfaces.client.KeyManagementSyncApiClient;
import com.czertainly.api.interfaces.client.LocationSyncApiClient;
import com.czertainly.api.interfaces.client.NotificationInstanceSyncApiClient;
import com.czertainly.api.interfaces.client.TokenInstanceSyncApiClient;
import com.czertainly.api.model.core.connector.ConnectorDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Factory that returns appropriate API client (REST or MQ) based on connector configuration.
 *
 * <p>This factory centralizes the logic for choosing between REST and MQ-based communication
 * with connectors. When a connector has a proxyId set and the corresponding MQ client is
 * available, the MQ client is returned. Otherwise, the REST client is used.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * AttributeSyncApiClient client = connectorApiFactory.getAttributeApiClient(connectorDto);
 * client.listAttributeDefinitions(connectorDto, functionGroup, kind);
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorApiFactory {

    // REST clients (always available)
    private final AttributeApiClient restAttributeApiClient;
    private final AuthorityInstanceApiClient restAuthorityInstanceApiClient;
    private final CertificateApiClient restCertificateApiClient;
    private final ConnectorApiClient restConnectorApiClient;
    private final DiscoveryApiClient restDiscoveryApiClient;
    private final EndEntityApiClient restEndEntityApiClient;
    private final EndEntityProfileApiClient restEndEntityProfileApiClient;
    private final EntityInstanceApiClient restEntityInstanceApiClient;
    private final HealthApiClient restHealthApiClient;
    private final LocationApiClient restLocationApiClient;
    private final NotificationInstanceApiClient restNotificationInstanceApiClient;
    private final TokenInstanceApiClient restTokenInstanceApiClient;
    private final KeyManagementApiClient restKeyManagementApiClient;
    private final CryptographicOperationsApiClient restCryptographicOperationsApiClient;
    private final com.czertainly.api.clients.v2.CertificateApiClient restCertificateApiClientV2;
    private final ComplianceApiClient restComplianceApiClient;
    private final com.czertainly.api.clients.v2.ComplianceApiClient restComplianceApiClientV2;

    // MQ clients (optional - Spring injects Optional.empty() if bean is missing)
    private final Optional<com.czertainly.api.clients.mq.AttributeApiClient> mqAttributeApiClient;
    private final Optional<com.czertainly.api.clients.mq.AuthorityInstanceApiClient> mqAuthorityInstanceApiClient;
    private final Optional<com.czertainly.api.clients.mq.CertificateApiClient> mqCertificateApiClient;
    private final Optional<com.czertainly.api.clients.mq.ConnectorApiClient> mqConnectorApiClient;
    private final Optional<com.czertainly.api.clients.mq.DiscoveryApiClient> mqDiscoveryApiClient;
    private final Optional<com.czertainly.api.clients.mq.EndEntityApiClient> mqEndEntityApiClient;
    private final Optional<com.czertainly.api.clients.mq.EndEntityProfileApiClient> mqEndEntityProfileApiClient;
    private final Optional<com.czertainly.api.clients.mq.EntityInstanceApiClient> mqEntityInstanceApiClient;
    private final Optional<com.czertainly.api.clients.mq.HealthApiClient> mqHealthApiClient;
    private final Optional<com.czertainly.api.clients.mq.LocationApiClient> mqLocationApiClient;
    private final Optional<com.czertainly.api.clients.mq.NotificationInstanceApiClient> mqNotificationInstanceApiClient;
    private final Optional<com.czertainly.api.clients.mq.TokenInstanceApiClient> mqTokenInstanceApiClient;
    private final Optional<com.czertainly.api.clients.mq.KeyManagementApiClient> mqKeyManagementApiClient;
    private final Optional<com.czertainly.api.clients.mq.CryptographicOperationsApiClient> mqCryptographicOperationsApiClient;
    private final Optional<com.czertainly.api.clients.mq.v2.CertificateApiClient> mqCertificateApiClientV2;
    private final Optional<com.czertainly.api.clients.mq.ComplianceApiClient> mqComplianceApiClient;
    private final Optional<com.czertainly.api.clients.mq.v2.ComplianceApiClient> mqComplianceApiClientV2;

    @PostConstruct
    void logInitialization() {
        log.info("ConnectorApiFactory initialized. MQ clients available: attribute={}, authorityInstance={}, certificate={}, certificateV2={}, compliance={}, complianceV2={}, connector={}, discovery={}, endEntity={}, endEntityProfile={}, entityInstance={}, health={}, location={}, notificationInstance={}, tokenInstance={}, keyManagement={}, cryptographicOperations={}",
                mqAttributeApiClient.isPresent(), mqAuthorityInstanceApiClient.isPresent(), mqCertificateApiClient.isPresent(), mqCertificateApiClientV2.isPresent(), mqComplianceApiClient.isPresent(), mqComplianceApiClientV2.isPresent(), mqConnectorApiClient.isPresent(), mqDiscoveryApiClient.isPresent(), mqEndEntityApiClient.isPresent(), mqEndEntityProfileApiClient.isPresent(), mqEntityInstanceApiClient.isPresent(), mqHealthApiClient.isPresent(), mqLocationApiClient.isPresent(), mqNotificationInstanceApiClient.isPresent(), mqTokenInstanceApiClient.isPresent(), mqKeyManagementApiClient.isPresent(), mqCryptographicOperationsApiClient.isPresent());
    }

    /**
     * Selects between REST and MQ client based on connector configuration.
     *
     * @param connector The connector configuration
     * @param restClient The REST client (always available)
     * @param mqClient The MQ client (may be empty if proxy/messaging is not enabled)
     * @return MQ client if connector has proxyId and MQ client is available, otherwise REST client
     */
    private <T> T getClient(ConnectorDto connector, T restClient, Optional<? extends T> mqClient) {
        Objects.requireNonNull(connector, "connector must not be null");
        if (shouldUseMq(connector) && mqClient.isPresent()) {
            log.debug("Using MQ client for connector {} via proxy {}", connector.getName(), connector.getProxy().getCode());
            return mqClient.get();
        }
        return restClient;
    }

    public AttributeSyncApiClient getAttributeApiClient(ConnectorDto connector) {
        return getClient(connector, restAttributeApiClient, mqAttributeApiClient);
    }

    public ConnectorSyncApiClient getConnectorApiClient(ConnectorDto connector) {
        return getClient(connector, restConnectorApiClient, mqConnectorApiClient);
    }

    public HealthSyncApiClient getHealthApiClient(ConnectorDto connector) {
        return getClient(connector, restHealthApiClient, mqHealthApiClient);
    }

    public DiscoverySyncApiClient getDiscoveryApiClient(ConnectorDto connector) {
        return getClient(connector, restDiscoveryApiClient, mqDiscoveryApiClient);
    }

    public EndEntityProfileSyncApiClient getEndEntityProfileApiClient(ConnectorDto connector) {
        return getClient(connector, restEndEntityProfileApiClient, mqEndEntityProfileApiClient);
    }

    public CertificateSyncApiClient getCertificateApiClient(ConnectorDto connector) {
        return getClient(connector, restCertificateApiClient, mqCertificateApiClient);
    }

    public AuthorityInstanceSyncApiClient getAuthorityInstanceApiClient(ConnectorDto connector) {
        return getClient(connector, restAuthorityInstanceApiClient, mqAuthorityInstanceApiClient);
    }

    public EntityInstanceSyncApiClient getEntityInstanceApiClient(ConnectorDto connector) {
        return getClient(connector, restEntityInstanceApiClient, mqEntityInstanceApiClient);
    }

    public LocationSyncApiClient getLocationApiClient(ConnectorDto connector) {
        return getClient(connector, restLocationApiClient, mqLocationApiClient);
    }

    public TokenInstanceSyncApiClient getTokenInstanceApiClient(ConnectorDto connector) {
        return getClient(connector, restTokenInstanceApiClient, mqTokenInstanceApiClient);
    }

    public KeyManagementSyncApiClient getKeyManagementApiClient(ConnectorDto connector) {
        return getClient(connector, restKeyManagementApiClient, mqKeyManagementApiClient);
    }

    public CryptographicOperationsSyncApiClient getCryptographicOperationsApiClient(ConnectorDto connector) {
        return getClient(connector, restCryptographicOperationsApiClient, mqCryptographicOperationsApiClient);
    }

    public CertificateSyncApiClientV2 getCertificateApiClientV2(ConnectorDto connector) {
        return getClient(connector, restCertificateApiClientV2, mqCertificateApiClientV2);
    }

    public ComplianceSyncApiClient getComplianceApiClient(ConnectorDto connector) {
        return getClient(connector, restComplianceApiClient, mqComplianceApiClient);
    }

    public ComplianceSyncApiClientV2 getComplianceApiClientV2(ConnectorDto connector) {
        return getClient(connector, restComplianceApiClientV2, mqComplianceApiClientV2);
    }

    public EndEntitySyncApiClient getEndEntityApiClient(ConnectorDto connector) {
        return getClient(connector, restEndEntityApiClient, mqEndEntityApiClient);
    }

    public NotificationInstanceSyncApiClient getNotificationInstanceApiClient(ConnectorDto connector) {
        return getClient(connector, restNotificationInstanceApiClient, mqNotificationInstanceApiClient);
    }

    /**
     * Check if MQ-based communication should be used for the given connector.
     *
     * @param connector Connector configuration
     * @return true if connector has a proxy with non-empty code set
     */
    private boolean shouldUseMq(ConnectorDto connector) {
        return connector.getProxy() != null && connector.getProxy().getCode() != null && !connector.getProxy().getCode().isBlank();
    }

}
