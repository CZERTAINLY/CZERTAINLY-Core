package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.DigitalSignatureController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureValidationResultDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.DigitalSignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DigitalSignatureControllerImpl implements DigitalSignatureController {

    private final DigitalSignatureService digitalSignatureService;

    @Autowired
    public DigitalSignatureControllerImpl(DigitalSignatureService digitalSignatureService) {
        this.digitalSignatureService = digitalSignatureService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.DIGITAL_SIGNATURE)
    @AuditLogged(module = Module.SIGNING, resource = Resource.DIGITAL_SIGNATURE, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return digitalSignatureService.getSearchableFieldInformation();
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.DIGITAL_SIGNATURE, operation = Operation.LIST)
    public PaginationResponseDto<DigitalSignatureListDto> listDigitalSignatures(SearchRequestDto request) {
        return digitalSignatureService.listDigitalSignatures(request, SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.DIGITAL_SIGNATURE, operation = Operation.DETAIL)
    public DigitalSignatureDto getDigitalSignature(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return digitalSignatureService.getDigitalSignature(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.DIGITAL_SIGNATURE, operation = Operation.DETAIL)
    public DigitalSignatureValidationResultDto validateDigitalSignature(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return digitalSignatureService.validateDigitalSignature(SecuredUUID.fromUUID(uuid));
    }
}
