package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeAccountRepository extends SecurityFilterRepository<AcmeAccount, Long> {
    Optional<AcmeAccount> findByUuid(String uuid);
    Optional<AcmeAccount> findByAccountId(String accountId);
    AcmeAccount findByPublicKey(String publicKey);
}
