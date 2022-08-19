package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface AcmeAccountService {

    void revokeAccount(SecuredUUID uuid) throws NotFoundException;

    void enableAccount(SecuredUUID uuid) throws NotFoundException;

    void disableAccount(SecuredUUID uuid) throws NotFoundException;

    void bulkEnableAccount(List<SecuredUUID> uuids);

    void bulkDisableAccount(List<SecuredUUID> uuids);

    void bulkRevokeAccount(List<SecuredUUID> uuids);

    List<AcmeAccountListResponseDto> listAcmeAccounts(SecurityFilter filter);

    AcmeAccountResponseDto getAcmeAccount(SecuredUUID uuid) throws NotFoundException;

}
