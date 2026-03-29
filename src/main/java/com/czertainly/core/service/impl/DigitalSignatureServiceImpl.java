package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureValidationResultDto;
import com.czertainly.core.dao.entity.signing.DigitalSignature;
import com.czertainly.core.mapper.signing.DigitalSignatureMapper;
import com.czertainly.core.dao.repository.signing.DigitalSignatureRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.DigitalSignatureService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DigitalSignatureServiceImpl implements DigitalSignatureService {

    private DigitalSignatureRepository digitalSignatureRepository;

    @Autowired
    public void setDigitalSignatureRepository(DigitalSignatureRepository digitalSignatureRepository) {
        this.digitalSignatureRepository = digitalSignatureRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DIGITAL_SIGNATURE, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.DIGITAL_SIGNATURE, action = ResourceAction.LIST)
    public PaginationResponseDto<DigitalSignatureListDto> listDigitalSignatures(SearchRequestDto request, SecurityFilter filter) {
        List<DigitalSignature> signatures = digitalSignatureRepository.findUsingSecurityFilter(filter);
        List<DigitalSignatureListDto> dtos = signatures.stream()
                .map(DigitalSignatureMapper::toListDto)
                .toList();
        PaginationResponseDto<DigitalSignatureListDto> response = new PaginationResponseDto<>();
        // :TODO: this is completely wrong
        response.setItemsPerPage(dtos.size());
        response.setPageNumber(1);
        response.setTotalItems(dtos.size());
        response.setTotalPages(1);
        response.setItems(dtos);
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DIGITAL_SIGNATURE, action = ResourceAction.DETAIL)
    public DigitalSignatureDto getDigitalSignature(SecuredUUID uuid) throws NotFoundException {
        DigitalSignature signature = digitalSignatureRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Digital Signature not found"));
        return DigitalSignatureMapper.toDto(signature);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DIGITAL_SIGNATURE, action = ResourceAction.DETAIL)
    public DigitalSignatureValidationResultDto validateDigitalSignature(SecuredUUID uuid) throws NotFoundException {
        DigitalSignature signature = digitalSignatureRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Digital Signature not found"));
        throw new UnsupportedOperationException("Digital signature validation not yet implemented");
    }
}
