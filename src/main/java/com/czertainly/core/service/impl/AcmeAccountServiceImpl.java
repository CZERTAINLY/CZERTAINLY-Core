package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.api.model.core.acme.OrderStatus;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AcmeAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcmeAccountServiceImpl implements AcmeAccountService {

    private static final Logger logger = LoggerFactory.getLogger(AcmeAccountServiceImpl.class);

    @Autowired
    private AcmeAccountRepository acmeAccountRepository;
    @Autowired
    private AcmeOrderRepository acmeOrderRepository;

    @Override
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.REVOKE, parentResource = Resource.ACME_PROFILE, parentAction = ResourceAction.DETAIL)
    public void revokeAccount(SecuredParentUUID acmeProfileUuid, SecuredUUID uuid) throws NotFoundException {
        revokeAccount(uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.ENABLE, parentResource = Resource.ACME_PROFILE, parentAction = ResourceAction.DETAIL)
    public void enableAccount(SecuredParentUUID acmeProfileUuid, SecuredUUID uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        if (!account.getStatus().equals(AccountStatus.VALID)) {
            throw new ValidationException(ValidationError.create("Cannot enable a revoked account"));
        }
        account.setEnabled(true);
        acmeAccountRepository.save(account);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.ENABLE, parentResource = Resource.ACME_PROFILE, parentAction = ResourceAction.DETAIL)
    public void disableAccount(SecuredParentUUID acmeProfileUuid, SecuredUUID uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        if (!account.getStatus().equals(AccountStatus.VALID)) {
            throw new ValidationException(ValidationError.create("Cannot disable a revoked account"));
        }
        account.setEnabled(false);
        acmeAccountRepository.save(account);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.ENABLE)
    public void bulkEnableAccount(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
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
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.ENABLE)
    public void bulkDisableAccount(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
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
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.REVOKE)
    public void bulkRevokeAccount(List<SecuredUUID> uuids) {
        for (SecuredUUID uuid : uuids) {
            try {
                revokeAccount(uuid);
            } catch (NotFoundException e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.LIST, parentResource = Resource.ACME_PROFILE, parentAction = ResourceAction.LIST)
    public List<AcmeAccountListResponseDto> listAcmeAccounts(SecurityFilter filter) {
        filter.setParentRefProperty("acmeProfileUuid");
        return acmeAccountRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(AcmeAccount::mapToDtoForUiSimple)
                .collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACME_ACCOUNT, action = ResourceAction.DETAIL, parentResource = Resource.ACME_PROFILE, parentAction = ResourceAction.DETAIL)
    public AcmeAccountResponseDto getAcmeAccount(SecuredParentUUID acmeProfileUuid, SecuredUUID uuid) throws NotFoundException {
        AcmeAccount acmeAccount = getAcmeAccountEntity(uuid);

        List<AcmeOrder> orders = acmeOrderRepository.findByAcmeAccountAndExpiresBefore(acmeAccount, new Date());
        for (AcmeOrder order : orders) {
            if (!order.getStatus().equals(OrderStatus.VALID)) {
                order.setStatus(OrderStatus.INVALID);
                acmeOrderRepository.save(order);
            }
        }

        return getAcmeAccountEntity(uuid).mapToDtoForUi();
    }

    private AcmeAccount getAcmeAccountEntity(SecuredUUID uuid) throws NotFoundException {
        return acmeAccountRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(AcmeAccount.class, uuid));
    }

    public void revokeAccount(SecuredUUID uuid) throws NotFoundException {
        AcmeAccount account = getAcmeAccountEntity(uuid);
        if (account.getStatus().equals(AccountStatus.REVOKED)) {
            throw new ValidationException(ValidationError.create("Cannot revoke a revoked account"));
        }
        account.setStatus(AccountStatus.REVOKED);
        account.setEnabled(false);
        acmeAccountRepository.save(account);
    }
}
