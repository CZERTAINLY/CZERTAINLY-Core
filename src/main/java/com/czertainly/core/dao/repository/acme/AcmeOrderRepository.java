package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeOrderRepository extends JpaRepository<AcmeOrder, Long> {
    Optional<AcmeOrder> findByUuid(String uuid);
    Optional<AcmeOrder> findByOrderId(String orderId);
}
