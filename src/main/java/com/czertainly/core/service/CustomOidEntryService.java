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
public interface CustomOidEntryService {

    /**
     * Creates a new custom OID entry.
     *
     * @param request the custom OID entry creation request
     * @return details of the created custom OID entry
     */
    CustomOidEntryDetailResponseDto createCustomOidEntry(CustomOidEntryRequestDto request);

    /**
     * Retrieves a custom OID entry by its OID value.
     *
     * @param oid the OID string
     * @return details of the requested custom OID entry
     */
    CustomOidEntryDetailResponseDto getCustomOidEntry(String oid) throws NotFoundException;

    /**
     * Edits an existing custom OID entry.
     *
     * @param oid     the OID to update
     * @param request the update request data
     * @return updated basic information of the custom OID entry
     */
    CustomOidEntryDetailResponseDto editCustomOidEntry(String oid, CustomOidEntryUpdateRequestDto request) throws NotFoundException;

    /**
     * Deletes a custom OID entry by its OID.
     *
     * @param oid the OID to delete
     */
    void deleteCustomOidEntry(String oid) throws NotFoundException;

    /**
     * Deletes multiple custom OID entries in batch.
     *
     * @param oids list of OIDs to delete
     */
    void bulkDeleteCustomOidEntry(List<String> oids);

    /**
     * Returns a filtered and paginated list of custom OID entries.
     *
     * @param request search and pagination criteria
     * @return list of OID entry responses matching the criteria
     */
    CustomOidEntryListResponseDto listCustomOidEntries(SearchRequestDto request);


    /**
     * Returns a list of properties for filtering custom OID entries
     *
     * @return list of properties for filtering OID entries
     */
    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();
}
