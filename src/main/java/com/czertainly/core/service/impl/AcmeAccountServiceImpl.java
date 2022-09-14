package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.service.AcmeAccountService;
import com.czertainly.core.service.acme.impl.ExtendedAcmeHelperService;
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
    @Autowired
    private ExtendedAcmeHelperService extendedAcmeHelperService;


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.REVOKE)
    public void revokeAccount(String uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        if(account.getStatus().equals(AccountStatus.REVOKED)){
            throw new ValidationException(ValidationError.create("Cannot revoke a revoked account"));
        }
        account.setStatus(AccountStatus.REVOKED);
        account.setEnabled(false);
        acmeAccountRepository.save(account);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.ENABLE)
    public void enableAccount(String uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        if(!account.getStatus().equals(AccountStatus.VALID)){
            throw new ValidationException(ValidationError.create("Cannot enable a revoked account"));
        }
        account.setEnabled(true);
        acmeAccountRepository.save(account);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.DISABLE)
    public void disableAccount(String uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        if(!account.getStatus().equals(AccountStatus.VALID)){
            throw new ValidationException(ValidationError.create("Cannot disable a revoked account"));
        }
        account.setEnabled(false);
        acmeAccountRepository.save(account);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.ENABLE)
    public void bulkEnableAccount(List<String> uuids) throws NotFoundException {
        for (String uuid : uuids) {
            try {
                AcmeAccount acmeAccount = getAcmeAccountEntity(uuid);
                if (!acmeAccount.getStatus().equals(AccountStatus.VALID)) {
                    logger.warn("Incorrect ACME Account status. Cannot enable");
                }
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
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.DISABLE)
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
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.REVOKE)
    public void bulkRevokeAccount(List<String> uuids) throws NotFoundException {
        for (String uuid : uuids) {
            try {
                revokeAccount(uuid);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.REQUEST)
    public List<AcmeAccountListResponseDto> listAcmeAccounts() {
        return acmeAccountRepository.findAll().stream().map(AcmeAccount::mapToDtoForUiSimple).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ACME_ACCOUNT, operation = OperationType.REQUEST)
    public AcmeAccountResponseDto getAcmeAccount(String uuid) throws NotFoundException {
        AcmeAccount acmeAccount = getAcmeAccountEntity(uuid);
        extendedAcmeHelperService.updateOrderStatusForAccount(acmeAccount);
        return getAcmeAccountEntity(uuid).mapToDtoForUi();
    }

    private AcmeAccount getAcmeAccountEntity(String uuid) throws NotFoundException {
        return acmeAccountRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(AcmeAccount.class, uuid));
    }
}
