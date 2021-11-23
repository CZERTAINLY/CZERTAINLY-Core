package com.czertainly.core.service;

import java.util.List;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.discovery.DiscoveryDto;
import com.czertainly.api.model.discovery.DiscoveryHistoryDto;

public interface DiscoveryService {

    List<DiscoveryHistoryDto> listDiscovery();
    DiscoveryHistoryDto getDiscovery(String uuid) throws NotFoundException;
    DiscoveryHistory createDiscoveryModal(DiscoveryDto request) throws AlreadyExistException, NotFoundException;

    void createDiscovery(DiscoveryDto request, DiscoveryHistory modal) throws AlreadyExistException, NotFoundException, ConnectorException;

    void removeDiscovery(String uuid) throws NotFoundException;
    void bulkRemoveDiscovery(List<String> discoveryUuids) throws NotFoundException;
}
