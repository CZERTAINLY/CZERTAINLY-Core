package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AcmeAccountController;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.core.service.AcmeAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AcmeAccountControllerImpl implements AcmeAccountController {

    @Autowired
    private AcmeAccountService acmeAccountService;

    @Override
    public void deleteAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.deleteAccount(uuid);
    }

    @Override
    public void bulkEnableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkEnableAccount(uuids);
    }

    @Override
    public void bulkDisableAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkDisableAccount(uuids);
    }

    @Override
    public void bulkDeleteAcmeAccount(List<String> uuids) throws NotFoundException {
        acmeAccountService.bulkDeleteAccount(uuids);
    }

    @Override
    public List<AcmeAccountListResponseDto> listAcmeAccount() {
        return acmeAccountService.listAcmeAccounts();
    }

    @Override
    public AcmeAccountResponseDto getAcmeAccount(String uuid) throws NotFoundException {
        return acmeAccountService.getAcmeAccount(uuid);
    }

    @Override
    public void enableAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.enableAccount(uuid);
    }

    @Override
    public void disableAcmeAccount(String uuid) throws NotFoundException {
        acmeAccountService.disableAccount(uuid);
    }
}
