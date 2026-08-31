package com.otilm.core.service.handler.token;

import com.otilm.api.model.client.connector.v2.ConnectorInterface;
import com.otilm.api.model.client.connector.v2.ConnectorVersion;
import com.otilm.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.client.ConnectorApiFactory;
import com.otilm.core.dao.entity.Connector;
import com.otilm.core.exception.UnsupportedCryptographyProviderVersionException;
import com.otilm.core.model.connector.ConnectorFunctionGroupModel;
import com.otilm.core.model.connector.ImmutableConnectorFullModel;
import com.otilm.core.model.connector.ImmutableConnectorInterface;
import com.otilm.core.model.crypto.ImmutableTokenInstanceFullModel;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.service.handler.OperationAttributeResolver;
import com.otilm.core.service.v2.ConnectorExternalService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenProviderAdapterFactoryTest {

    private TokenProviderAdapterFactory factory;
    private ConnectorExternalService connectorExternalService;

    @BeforeEach
    void setUp() {
        connectorExternalService = mock(ConnectorExternalService.class);
        factory = new TokenProviderAdapterFactory(mock(ConnectorApiFactory.class), connectorExternalService,
                mock(AttributeEngine.class), mock(OperationAttributeResolver.class));
    }

    @Test
    void forConnector_returnsV1Adapter_forLegacyProvider() {
        // given
        var legacyProvider = new ConnectorFunctionGroupModel(UUID.randomUUID(), "provider",
                FunctionGroupCode.CRYPTOGRAPHY_PROVIDER, List.of());
        ImmutableConnectorFullModel connector = connector(List.of(), List.of(legacyProvider));

        // when
        TokenProviderAdapter adapter = factory.forConnector(connector);

        // then
        assertInstanceOf(TokenProviderV1Adapter.class, adapter);
    }

    @Test
    void forConnector_returnsV2Adapter_forCryptographyV2Interface() {
        // given
        ImmutableConnectorFullModel connector = connector(List.of(cryptographyInterface("v2")), List.of());

        // when
        TokenProviderAdapter adapter = factory.forConnector(connector);

        // then
        assertInstanceOf(TokenProviderV2Adapter.class, adapter);
    }

    @Test
    void forConnector_throws_forUnsupportedInterfaceVersion() {
        // given
        ImmutableConnectorFullModel connector = connector(List.of(cryptographyInterface("v3")), List.of());

        // when
        Executable selectAdapter = () -> factory.forConnector(connector);

        // then
        assertThrows(UnsupportedCryptographyProviderVersionException.class, selectAdapter);
    }

    @Test
    void forToken_returnsV2Adapter_forPersistedCryptographyInterface() throws Exception {
        // given
        UUID connectorUuid = UUID.randomUUID();
        ImmutableConnectorFullModel connector = connector(List.of(cryptographyInterface("v2")), List.of());
        when(connectorExternalService.getConnectorFullModel(SecuredUUID.fromUUID(connectorUuid))).thenReturn(connector);
        var token = new ImmutableTokenInstanceFullModel(UUID.randomUUID(), null, "token", TokenInstanceStatus.UNKNOWN,
                "SOFT", connectorUuid, "connector", connector.connectorInterfaces().get(0).uuid(),
                mock(Connector.class), connector.connectorInterfaces().get(0), Set.of());

        // when
        TokenProviderAdapter adapter = factory.forToken(token);

        // then
        assertInstanceOf(TokenProviderV2Adapter.class, adapter);
    }

    private ImmutableConnectorFullModel connector(List<ImmutableConnectorInterface> interfaces,
            List<ConnectorFunctionGroupModel> functionGroups) {
        return new ImmutableConnectorFullModel(UUID.randomUUID(), "connector", ConnectorVersion.V2, "http://localhost",
                null, List.of(), null, null, interfaces, functionGroups);
    }

    private ImmutableConnectorInterface cryptographyInterface(String version) {
        return new ImmutableConnectorInterface(UUID.randomUUID(), ConnectorInterface.CRYPTOGRAPHY, version, List.of());
    }
}
