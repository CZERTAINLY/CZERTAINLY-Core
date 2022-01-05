package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeAccountRepository extends JpaRepository<AcmeAccount, Long> {
    Optional<AcmeAccount> findByUuid(String uuid);
    Optional<AcmeAccount> findByAccountId(String accountId);
}
