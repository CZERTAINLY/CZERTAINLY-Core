package com.czertainly.core.dao.repository.cmp;

import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmpTransactionRepository extends SecurityFilterRepository<CmpTransaction, UUID> {

    Optional<CmpTransaction> findByTransactionId(String transactionId);

}
