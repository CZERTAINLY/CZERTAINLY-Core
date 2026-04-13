package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordDto;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordListDto;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordValidationResultDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface SigningRecordService {

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    PaginationResponseDto<SigningRecordListDto> listSigningRecords(SearchRequestDto request, SecurityFilter filter);

    SigningRecordDto getSigningRecord(SecuredUUID uuid) throws NotFoundException;

    SigningRecordValidationResultDto validateSigningRecord(SecuredUUID uuid) throws NotFoundException;
}
