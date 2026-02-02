package com.czertainly.core.service;

import java.util.List;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.cbom.CbomDetailDto;
import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.api.model.core.cbom.CbomUploadRequestDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

public interface CbomService extends ResourceExtensionService {
    List<CbomDto> listCboms(SecurityFilter filter);
    CbomDto getCbom(SecuredUUID uuid) throws NotFoundException;
    CbomDetailDto getCbomDetail(SecuredUUID uuid) throws NotFoundException;
    List<CbomDto> getCbomVersions(String serialNumber);

    CbomDto createCbom(CbomUploadRequestDto request) throws ValidationException, AlreadyExistException;
}
