package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AcmeAccountController;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
import com.czertainly.core.service.AcmeAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AcmeAccountControllerImpl implements AcmeAccountController {

    @Autowired
    private AcmeAccountService acmeAccountService;

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.REVOKE)
    public void revokeAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.revokeAccount(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.ENABLE)
    public void bulkEnableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkEnableAccount(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.DISABLE)
    public void bulkDisableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkDisableAccount(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.REVOKE)
    public void bulkRevokeAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkRevokeAccount(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.LIST, isListingEndPoint = true)
    public List<AcmeAccountListResponseDto> listAcmeAccounts() {
        return acmeAccountService.listAcmeAccounts();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.DETAIL)
    public AcmeAccountResponseDto getAcmeAccount(String uuid) throws NotFoundException {
        return acmeAccountService.getAcmeAccount(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.ENABLE)
    public void enableAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.enableAccount(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.ACME_ACCOUNT, actionName = ActionName.DISABLE)
    public void disableAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.disableAccount(uuid);
    }
}
