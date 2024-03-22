package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface DiscoveryService extends ResourceExtensionService {

    DiscoveryResponseDto listDiscoveries(final SecurityFilter filter, final SearchRequestDto searchRequestDto);

    DiscoveryHistoryDetailDto getDiscovery(SecuredUUID uuid) throws NotFoundException;

    /**
     * List the certificates that are discovered as part of the discovery
     *
     * @param uuid            UUID of the discovery
     * @param newlyDiscovered Boolean representing of the certificate is newly discovered or existing
     * @param itemsPerPage    Pagination Item - Number of items per page
     * @param pageNumber      Page number
     * @return List of certificates
     * @throws NotFoundException when the discovery with the UUID is not found
     */
    DiscoveryCertificateResponseDto getDiscoveryCertificates(SecuredUUID uuid, Boolean newlyDiscovered, int itemsPerPage, int pageNumber) throws NotFoundException;

    DiscoveryHistory createDiscoveryModal(DiscoveryDto request, boolean saveEntity) throws AlreadyExistException, ConnectorException, AttributeException;


    void createDiscovery(DiscoveryHistory modal) throws AlreadyExistException, ConnectorException;
    void createDiscoveryAsync(DiscoveryHistory modal) throws AlreadyExistException, ConnectorException;

    void deleteDiscovery(SecuredUUID uuid) throws NotFoundException;

    void bulkRemoveDiscovery(List<SecuredUUID> discoveryUuids) throws NotFoundException;

    /**
     * Get the number of discoveries per user for dashboard
     *
     * @return Number of discoveries
     */
    Long statisticsDiscoveryCount(SecurityFilter filter);

    List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup();

}