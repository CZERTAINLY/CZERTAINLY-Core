package com.otilm.core.model.connector;

import com.otilm.api.model.client.attribute.ResponseAttribute;
import com.otilm.api.model.client.connector.v2.ConnectorVersion;
import com.otilm.api.model.core.connector.AuthType;
import com.otilm.api.model.core.connector.ConnectorStatus;
import com.otilm.api.model.core.proxy.ProxyDto;

import java.util.List;
import java.util.UUID;

public interface ConnectorFullModel {
    UUID uuid();

    String name();

    ConnectorVersion version();

    String url();

    AuthType authType();

    List<ResponseAttribute> authAttributes();

    ConnectorStatus status();

    ProxyDto proxy();

    List<ImmutableConnectorInterface> connectorInterfaces();

    List<ConnectorFunctionGroupModel> functionGroups();
}
