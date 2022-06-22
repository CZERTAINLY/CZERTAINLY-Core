package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.core.dao.entity.Connector;

public interface ComplianceConnectorService {
    /**
     * Fetch the list of groups and rules from the compliance provider and add them into the database
     * @param connector Connector Entity which implements CREDENTIAL_PROVIDER functional group
     * @throws ConnectorException Raises when there are issues with communicating with the connector
     */
    void addFetchGroupsAndRules(Connector connector) throws ConnectorException;

    /**
     * Fetch the list of groups and rules from the compliance provider and update them into the database
     * @param connector Connector Entity which implements CREDENTIAL_PROVIDER functional group
     * @throws ConnectorException Raises when there are issues with communicating with the connector
     */
    void updateGroupsAndRules(Connector connector) throws ConnectorException;
}
