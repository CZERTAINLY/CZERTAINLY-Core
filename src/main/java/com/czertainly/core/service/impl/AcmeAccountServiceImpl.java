package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.service.AcmeAccountService;
import com.czertainly.core.service.AcmeProfileService;
import com.czertainly.core.util.AcmeSerializationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_SUPERADMINISTRATOR", "ROLE_ADMINISTARTOR"})
public class AcmeAccountServiceImpl implements AcmeAccountService {

    private static final Logger logger = LoggerFactory.getLogger(AcmeAccountServiceImpl.class);

    @Autowired
    private AcmeAccountRepository acmeAccountRepository;

    @Override
    public void deleteAccount(String uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        acmeAccountRepository.delete(account);
    }

    @Override
    public void enableAccount(String uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        account.setEnabled(true);
        acmeAccountRepository.save(account);
    }

    @Override
    public void disableAccount(String uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        account.setEnabled(false);
        acmeAccountRepository.save(account);
    }

    @Override
    public void bulkEnableAccount(List<String> uuids) throws NotFoundException {
        for (String uuid : uuids) {
            try {
                AcmeAccount acmeAccount = getAcmeAccountEntity(uuid);
                if (acmeAccount.isEnabled()) {
                    logger.warn("ACME Account is already enabled");
                }
                acmeAccount.setEnabled(true);
                acmeAccountRepository.save(acmeAccount);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    public void bulkDisableAccount(List<String> uuids) throws NotFoundException {
        for (String uuid : uuids) {
            try {
                AcmeAccount acmeAccount = getAcmeAccountEntity(uuid);
                if (!acmeAccount.isEnabled()) {
                    logger.warn("ACME Account is already disabled");
                }
                acmeAccount.setEnabled(false);
                acmeAccountRepository.save(acmeAccount);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    public void bulkDeleteAccount(List<String> uuids) throws NotFoundException {
        for (String uuid : uuids) {
            try {
                AcmeAccount acmeAccount = getAcmeAccountEntity(uuid);
                acmeAccountRepository.delete(acmeAccount);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    public List<AcmeAccountListResponseDto> listAcmeAccounts() {
        return acmeAccountRepository.findAll().stream().map(AcmeAccount::mapToDtoForUiSimple).collect(Collectors.toList());
    }

    @Override
    public AcmeAccountResponseDto getAcmeAccount(String uuid) throws NotFoundException {
        return getAcmeAccountEntity(uuid).mapToDtoForUi();
    }

    private AcmeAccount getAcmeAccountEntity(String uuid) throws NotFoundException {
        return acmeAccountRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(AcmeAccount.class, uuid));
    }
}
