package com.czertainly.core.api.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.CbomController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomListResponseDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.cbom.client.CbomRepositoryException;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CbomService;

@RestController
public class CbomControllerImpl implements CbomController {

    private CbomService cbomService;

    @Autowired
    public void setCbomService(CbomService cbomService) {
        this.cbomService = cbomService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CBOM)
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.LIST)
	public CbomListResponseDto listCboms(SearchRequestDto request) {
        throw new Exception("not yet implemented");
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CBOM)
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.LIST)
	public CbomDetailDto getCbomDetail(String uuid, String version)	throws NotFoundException, CbomRepositoryException {
        return cbomService.getCbomDetail(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CBOM)
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.LIST)
	public List<CbomDto> listCbomVersions(String serialNumber) throws NotFoundException {
        return cbomService.getCbomVersions(serialNumber);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.CBOM)
    @AuditLogged(
        module = Module.CORE,
        resource = Resource.CBOM,
        operation = Operation.CREATE)
	public CbomDto uploadCbom(CbomUploadRequestDto request) throws AttributeException {
        CbomDto cbomDto = cbomService.createCbom(request);
        return cbomDto;
    }
}
