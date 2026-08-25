package com.otilm.core.service.impl;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.NotSupportedException;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.dashboard.CryptographicAssetStatisticsDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDetailDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDto;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.security.authz.ExternalAuthorization;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.CryptographicAssetExternalService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Serves the ratified cryptographic asset inventory contract with a refusal on every operation: the inventory
 * projection over stored CBOM documents does not exist yet, so there is nothing to read. The authorization annotations
 * are the working part — they register {@code cryptoAssets:list} and {@code cryptoAssets:detail} with the auth service
 * on context refresh, which is what lets roles be defined and the frontend's generated client be wired against the real
 * gates while the read model is built.
 *
 * <p>
 * {@link NotSupportedException} maps to HTTP 501 in {@code ExceptionHandlingAdvice}, so a permitted caller learns the
 * operation is not implemented while an unpermitted one is still refused with 403 by the authorization aspect ahead of
 * this body.
 */
@Service
public class CryptographicAssetServiceImpl implements CryptographicAssetExternalService {

    private static final String NOT_IMPLEMENTED = "Cryptographic asset inventory is not implemented yet";

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.LIST)
    public PaginationResponseDto<CryptographicAssetDto> listCryptographicAssets(SecurityFilter filter,
            SearchRequestDto request) {
        throw new NotSupportedException(NOT_IMPLEMENTED);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.DETAIL)
    public CryptographicAssetDetailDto getCryptographicAsset(SecuredUUID uuid) throws NotFoundException {
        throw new NotSupportedException(NOT_IMPLEMENTED);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {
        throw new NotSupportedException(NOT_IMPLEMENTED);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CRYPTO_ASSET, action = ResourceAction.LIST)
    public CryptographicAssetStatisticsDto getCryptographicAssetStatistics() {
        throw new NotSupportedException(NOT_IMPLEMENTED);
    }
}
