package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.oid.*;

import java.util.List;

/**
 * Service for managing OID entries.
 */
public interface OidEntryService extends ResourceExtensionService {

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

    String getDisplayName(String oid);

    String getCode(String oid);
}
