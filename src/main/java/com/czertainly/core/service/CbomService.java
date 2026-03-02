package com.czertainly.core.service;

import java.util.List;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

public interface CbomService extends ResourceExtensionService {
    /**
     * List available CBOMs
     * @return List of available CBOMs
     */
    PaginationResponseDto<CbomDto> listCboms(SecurityFilter filter, SearchRequestDto request);

    /**
     * Retrieves detailed information about a specific CBOM
     * from cbom-repository.
     *
     * @param uuid Secured unique identifier of the CBOM to retrieve
     * @return {@link CbomDetailDto} containing the complete CBOM details
     * @throws CbomRepositoryException if there is an error accessing the CBOM repository
     * @throws NotFoundException if the CBOM with the specified UUID does not exist
     */
    CbomDetailDto getCbomDetail(SecuredUUID uuid) throws CbomRepositoryException, NotFoundException;

    /**
     * Return versions of CBOM with same serial number from DB
     * @param uuid Secured unique identifier of the CBOM to retrieve
     * @return List of uuid/urn/version
     * @throws NotFoundException if the CBOM with the specified UUID does not exist
     */
    List<CbomDto> getCbomVersions(SecuredUUID uuid) throws NotFoundException;

    /**
     * Upload CBOM into cbom-repository and store cbom statistics in database.
     * @param request is upload request, see {@link CbomUploadRequestDto}
     * @return Stats of created instance
     * @throws AlreadyExistException if a CBOM with given serial number exists
     * @throws CbomRepositoryException for a unspecified problem with accessing cbom-repository (like HTTP 502)
     * @throws ValidationException if CBOM does not pass the cbom-repository validation
     */
    CbomDto createCbom(CbomUploadRequestDto request) throws AlreadyExistException, CbomRepositoryException, ValidationException;


    /**
     * Get all searchable fields for CBOM filtering
     * @return List of {@link SearchFieldDataByGroupDto} object with definition the possible fields
     */
    List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup();
}
