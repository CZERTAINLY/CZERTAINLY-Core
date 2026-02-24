package com.czertainly.core.service.handler;

import com.czertainly.api.clients.ApiClientConnectorInfo;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.connector.v2.ConnectorInfo;
import com.czertainly.api.model.client.connector.v2.ConnectorVersion;
import com.czertainly.api.model.client.connector.v2.HealthInfo;
import com.czertainly.api.model.core.connector.v2.ConnectInfo;
import com.czertainly.core.dao.entity.Connector;

public interface ConnectorAdapter {

    ConnectorVersion getVersion();

    ConnectorInfo getInfo(ApiClientConnectorInfo connectorInfo) throws ConnectorException;

    HealthInfo checkHealth(ApiClientConnectorInfo connectorInfo) throws ConnectorException;

    ConnectInfo checkConnection(ApiClientConnectorInfo connectorInfo) throws ConnectorException;

    ConnectInfo validateConnection(ApiClientConnectorInfo connectorInfo) throws ConnectorException;

    void updateConnectorFunctions(Connector connector, ConnectInfo connectInfo) throws ConnectorException, NotFoundException;

}
