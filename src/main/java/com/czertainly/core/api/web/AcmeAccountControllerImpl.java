package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AcmeAccountController;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.AcmeAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AcmeAccountControllerImpl implements AcmeAccountController {

    @Autowired
    private AcmeAccountService acmeAccountService;

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.REVOKE)
    public void revokeAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.revokeAccount(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.ENABLE)
    public void bulkEnableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkEnableAccount(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.DISABLE)
    public void bulkDisableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkDisableAccount(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.REVOKE)
    public void bulkRevokeAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkRevokeAccount(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.LIST, isListingEndPoint = true)
    public List<AcmeAccountListResponseDto> listAcmeAccounts() {
        return acmeAccountService.listAcmeAccounts();
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.DETAIL)
    public AcmeAccountResponseDto getAcmeAccount(String uuid) throws NotFoundException {
        return acmeAccountService.getAcmeAccount(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.ENABLE)
    public void enableAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.enableAccount(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.ACME_ACCOUNT, actionName = ResourceAction.DISABLE)
    public void disableAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.disableAccount(uuid);
    }
}
