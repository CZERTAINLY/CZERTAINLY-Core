package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.oid.*;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;

import java.util.List;
import java.util.Map;

/**
 * Service for managing OID entries.
 */
public interface OidEntryService {

    /**
     * Creates a new OID entry.
     *
     * @param request the OID entry creation request
     * @return details of the created OID entry
     */
    OidEntryDetailResponseDto createOidEntry(OidEntryRequestDto request);

    /**
     * Retrieves an OID entry by its OID value.
     *
     * @param oid the OID string
     * @return details of the requested OID entry
     */
    OidEntryDetailResponseDto getOidEntry(String oid) throws NotFoundException;

    /**
     * Edits an existing OID entry.
     *
     * @param oid     the OID to update
     * @param request the update request data
     * @return updated basic information of the OID entry
     */
    OidEntryResponseDto editOidEntry(String oid, OidEntryUpdateRequestDto request) throws NotFoundException;

    /**
     * Deletes an OID entry by its OID.
     *
     * @param oid the OID to delete
     */
    void deleteOidEntry(String oid) throws NotFoundException;

    /**
     * Deletes multiple OID entries in batch.
     *
     * @param oids list of OIDs to delete
     */
    void bulkDeleteOidEntry(List<String> oids);

    /**
     * Returns a filtered and paginated list of OID entries.
     *
     * @param request search and pagination criteria
     * @return list of OID entry responses matching the criteria
     */
    OidEntryListResponseDto listOidEntries(SearchRequestDto request);


    /**
     * Returns a list of properties for filtering OID entries
     *
     * @return list of properties for filtering OID entries
     */
    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    /**
     * Returns a map of OID to display name for given category
     *
     * @param oidCategory category to retrieve map for
     * @return map of OID to display name
     */
    Map<String, String> getOidToDisplayNameMap(OidCategory oidCategory);

    /**
     * Returns a map of OID to code for RDN Attribute Type
     *
     * @return map of OID to display name
     */
    Map<String, String> getOidToCodeMap();
}
