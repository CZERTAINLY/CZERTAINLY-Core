package com.czertainly.core.api.web;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.CbomController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CbomService;

@RestController
public class CbomControllerImpl implements CbomController {

    private CbomService cbomService;

    @Autowired
    public void setCbomService(CbomService cbomService) {
        this.cbomService = cbomService;
    }

    @Override
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.LIST)
    public PaginationResponseDto<CbomDto> listCboms(SearchRequestDto requestDto) {
        return cbomService.listCboms(SecurityFilter.create(), requestDto);
    }

    @Override
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.DETAIL)
    public CbomDetailDto getCbomDetail(@LogResource(uuid = true) UUID uuid) throws NotFoundException, CbomRepositoryException {
        return cbomService.getCbomDetail(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.LIST_VERSIONS)
    public List<CbomDto> listCbomVersions(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return cbomService.getCbomVersions(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.CREATE)
    public CbomDto uploadCbom(CbomUploadRequestDto request) throws ValidationException, AlreadyExistException, CbomRepositoryException {
        return cbomService.createCbom(request);
    }

    @Override
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.SEARCH_FILTER,
        affiliatedResource = Resource.CBOM,
        operation = Operation.LIST
    )
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation(){
        return cbomService.getSearchableFieldInformationByGroup();
    }
}
