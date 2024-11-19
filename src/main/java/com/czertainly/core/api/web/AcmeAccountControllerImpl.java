package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AcmeAccountController;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AcmeAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AcmeAccountControllerImpl implements AcmeAccountController {

    @Autowired
    private AcmeAccountService acmeAccountService;

    @Override
    public void revokeAcmeAccount(String acmeProfileUuid, String acmeAccountUuid) throws NotFoundException {
        acmeAccountService.revokeAccount(SecuredParentUUID.fromString(acmeProfileUuid), SecuredUUID.fromString(acmeAccountUuid));
    }

    @Override
    public void bulkEnableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkEnableAccount(SecuredUUID.fromList(uuids));
    }

    @Override
    public void bulkDisableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkDisableAccount(SecuredUUID.fromList(uuids));
    }

    @Override
    public void bulkRevokeAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkRevokeAccount(SecuredUUID.fromList(uuids));
    }

    @Override
    public List<AcmeAccountListResponseDto> listAcmeAccounts() {
        return acmeAccountService.listAcmeAccounts(SecurityFilter.create());
    }

    @Override
    public AcmeAccountResponseDto getAcmeAccount(String acmeProfileUuid, String acmeAccountUuid) throws NotFoundException {
        return acmeAccountService.getAcmeAccount(SecuredParentUUID.fromString(acmeProfileUuid), SecuredUUID.fromString(acmeAccountUuid));
    }

    @Override
    public void enableAcmeAccount(String acmeProfileUuid, String acmeAccountUuid) throws NotFoundException {
        acmeAccountService.enableAccount(SecuredParentUUID.fromString(acmeProfileUuid), SecuredUUID.fromString(acmeAccountUuid));
    }

    @Override
    public void disableAcmeAccount(String acmeProfileUuid, String acmeAccountUuid) throws NotFoundException {
        acmeAccountService.disableAccount(SecuredParentUUID.fromString(acmeProfileUuid), SecuredUUID.fromString(acmeAccountUuid));
    }
}
