package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.dao.entity.Connector;

public interface ComplianceConnectorService {
    void addFetchGroupsAndRules(Connector connector) throws ConnectorException;

    void updateGroupsAndRules(Connector connector) throws ConnectorException;
}
