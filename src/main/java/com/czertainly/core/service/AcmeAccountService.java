package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;

import java.util.List;

public interface AcmeAccountService {

    void revokeAccount(String uuid) throws NotFoundException;

    void enableAccount(String uuid) throws NotFoundException;

    void disableAccount(String uuid) throws NotFoundException;

    void bulkEnableAccount(List<String> uuids) throws NotFoundException;

    void bulkDisableAccount(List<String> uuids) throws NotFoundException;

    void bulkRevokeAccount(List<String> uuids) throws NotFoundException;

    List<AcmeAccountListResponseDto> listAcmeAccounts();

    AcmeAccountResponseDto getAcmeAccount(String uuid) throws NotFoundException;

}
