package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.core.discovery.DiscoveryHistoryDto;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface DiscoveryService {

    List<DiscoveryHistoryDto> listDiscoveries(SecurityFilter filter);
    DiscoveryHistoryDto getDiscovery(SecuredUUID uuid) throws NotFoundException;
    DiscoveryHistory createDiscoveryModal(DiscoveryDto request) throws AlreadyExistException, ConnectorException;

    void createDiscovery(DiscoveryDto request, DiscoveryHistory modal) throws AlreadyExistException, NotFoundException, ConnectorException;

    void deleteDiscovery(SecuredUUID uuid) throws NotFoundException;
    void bulkRemoveDiscovery(List<SecuredUUID> discoveryUuids) throws NotFoundException;

    /**
     * Get the number of discoveries per user for dashboard
     * @return Number of discoveries
     */
    Long statisticsDiscoveryCount(SecurityFilter filter);
}
