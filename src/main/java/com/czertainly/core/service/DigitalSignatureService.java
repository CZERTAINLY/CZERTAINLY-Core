package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureValidationResultDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface DigitalSignatureService {

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    PaginationResponseDto<DigitalSignatureListDto> listDigitalSignatures(SearchRequestDto request, SecurityFilter filter);

    DigitalSignatureDto getDigitalSignature(SecuredUUID uuid) throws NotFoundException;

    DigitalSignatureValidationResultDto validateDigitalSignature(SecuredUUID uuid) throws NotFoundException;
}
