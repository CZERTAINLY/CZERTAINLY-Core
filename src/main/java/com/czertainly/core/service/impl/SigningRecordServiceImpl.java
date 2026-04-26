package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordDto;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordListDto;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordValidationResultDto;
import com.czertainly.core.dao.entity.signing.SigningRecord;
import com.czertainly.core.mapper.signing.SigningRecordMapper;
import com.czertainly.core.dao.repository.signing.SigningRecordRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.SigningRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SigningRecordServiceImpl implements SigningRecordService {

    private SigningRecordRepository signingRecordRepository;

    @Autowired
    public void setSigningRecordRepository(SigningRecordRepository signingRecordRepository) {
        this.signingRecordRepository = signingRecordRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_RECORD, action = ResourceAction.LIST)
    @Transactional(readOnly = true)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_RECORD, action = ResourceAction.LIST)
    @Transactional(readOnly = true)
    public PaginationResponseDto<SigningRecordListDto> listSigningRecords(SearchRequestDto request, SecurityFilter filter) {
        List<SigningRecord> records = signingRecordRepository.findUsingSecurityFilter(filter);
        List<SigningRecordListDto> dtos = records.stream()
                .map(SigningRecordMapper::toListDto)
                .toList();
        PaginationResponseDto<SigningRecordListDto> response = new PaginationResponseDto<>();
        // :TODO: this is completely wrong
        response.setItemsPerPage(dtos.size());
        response.setPageNumber(1);
        response.setTotalItems(dtos.size());
        response.setTotalPages(1);
        response.setItems(dtos);
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_RECORD, action = ResourceAction.DETAIL)
    @Transactional(readOnly = true)
    public SigningRecordDto getSigningRecord(SecuredUUID uuid) throws NotFoundException {
        SigningRecord record = signingRecordRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Signing Record not found"));
        return SigningRecordMapper.toDto(record);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_RECORD, action = ResourceAction.DETAIL)
    @Transactional(readOnly = true)
    public SigningRecordValidationResultDto validateSigningRecord(SecuredUUID uuid) throws NotFoundException {
        signingRecordRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Signing Record not found"));
        throw new UnsupportedOperationException("Signing record validation not yet implemented");
    }
}
