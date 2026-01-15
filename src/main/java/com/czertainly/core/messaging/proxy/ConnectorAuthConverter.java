package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.BaseApiClient;
import com.czertainly.api.clients.mq.model.ConnectorAuth;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.FileAttributeContent;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.core.util.AttributeDefinitionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts ConnectorDto authentication information to proxy message format.
 * Maps CZERTAINLY auth types and attributes to the proxy's expected ConnectorAuth structure.
 */
@Slf4j
@Component
public class ConnectorAuthConverter {

    /**
     * Convert ConnectorDto authentication to proxy ConnectorAuth format.
     *
     * @param connector The connector with auth configuration
     * @return ConnectorAuth for proxy message
     */
    public ConnectorAuth convert(ConnectorDto connector) {
        AuthType authType = connector.getAuthType();

        // Default to NONE for backward compatibility
        if (authType == null) {
            log.debug("No auth type specified for connector, using NONE");
            return ConnectorAuth.builder()
                    .type("NONE")
                    .attributes(Map.of())
                    .build();
        }

        List<ResponseAttributeDto> authAttributes = connector.getAuthAttributes();

        return switch (authType) {
            case NONE -> ConnectorAuth.builder()
                    .type("NONE")
                    .attributes(Map.of())
                    .build();

            case BASIC -> convertBasicAuth(authAttributes);

            case API_KEY -> convertApiKeyAuth(authAttributes);

            case CERTIFICATE -> convertCertificateAuth(authAttributes);

            case JWT -> throw new UnsupportedOperationException("JWT authentication not yet implemented");
        };
    }

    /**
     * Convert BASIC authentication.
     */
    private ConnectorAuth convertBasicAuth(List<ResponseAttributeDto> authAttributes) {
        BaseAttributeContent<String> username = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_USERNAME, authAttributes, true);
        BaseAttributeContent<String> password = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_PASSWORD, authAttributes, true);

        Map<String, Object> attributes = new HashMap<>();
        if (username != null && username.getData() != null) {
            attributes.put("username", username.getData());
        }
        if (password != null && password.getData() != null) {
            attributes.put("password", password.getData());
        }

        log.debug("Converted BASIC auth with username: {}", username != null && username.getData() != null ? "present" : "absent");

        return ConnectorAuth.builder()
                .type("BASIC")
                .attributes(attributes)
                .build();
    }

    /**
     * Convert API_KEY authentication.
     */
    private ConnectorAuth convertApiKeyAuth(List<ResponseAttributeDto> authAttributes) {
        BaseAttributeContent<String> apiKeyHeader = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_API_KEY_HEADER, authAttributes, true);
        BaseAttributeContent<String> apiKey = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_API_KEY, authAttributes, true);

        Map<String, Object> attributes = new HashMap<>();
        if (apiKeyHeader != null && apiKeyHeader.getData() != null) {
            attributes.put("headerName", apiKeyHeader.getData());
        }
        if (apiKey != null && apiKey.getData() != null) {
            attributes.put("apiKey", apiKey.getData());
        }

        log.debug("Converted API_KEY auth with header: {}", apiKeyHeader != null ? apiKeyHeader.getData() : "null");

        return ConnectorAuth.builder()
                .type("API_KEY")
                .attributes(attributes)
                .build();
    }

    /**
     * Convert CERTIFICATE (mTLS) authentication.
     * The proxy expects base64-encoded keystore and truststore in PKCS12 format.
     */
    private ConnectorAuth convertCertificateAuth(List<ResponseAttributeDto> authAttributes) {
        Map<String, Object> attributes = new HashMap<>();

        // Keystore (client certificate + private key)
        FileAttributeContent keyStoreData = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_KEYSTORE, authAttributes, true);
        if (keyStoreData != null && keyStoreData.getData() != null && keyStoreData.getData().getContent() != null) {
            attributes.put("keystore", keyStoreData.getData().getContent());
        }

        BaseAttributeContent<String> keyStorePassword = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_KEYSTORE_PASSWORD, authAttributes, true);
        if (keyStorePassword != null && keyStorePassword.getData() != null) {
            attributes.put("keystorePassword", keyStorePassword.getData());
        }

        // Truststore (CA certificates)
        FileAttributeContent trustStoreData = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_TRUSTSTORE, authAttributes, true);
        if (trustStoreData != null && trustStoreData.getData() != null && trustStoreData.getData().getContent() != null) {
            attributes.put("truststore", trustStoreData.getData().getContent());
        }

        BaseAttributeContent<String> trustStorePassword = AttributeDefinitionUtils.getAttributeContent(
                BaseApiClient.ATTRIBUTE_TRUSTSTORE_PASSWORD, authAttributes, true);
        if (trustStorePassword != null && trustStorePassword.getData() != null) {
            attributes.put("truststorePassword", trustStorePassword.getData());
        }

        log.debug("Converted CERTIFICATE auth with keystore: {}, truststore: {}",
                keyStoreData != null ? "present" : "absent",
                trustStoreData != null ? "present" : "absent");

        return ConnectorAuth.builder()
                .type("CERTIFICATE")
                .attributes(attributes)
                .build();
    }
}
