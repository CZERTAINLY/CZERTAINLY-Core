package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface AcmeOrderRepository extends SecurityFilterRepository<AcmeOrder, Long> {
    Optional<AcmeOrder> findByUuid(UUID uuid);
    Optional<AcmeOrder> findByOrderId(String orderId);
    Optional<AcmeOrder> findByCertificateId(String certificateId);
    List<AcmeOrder> findByAcmeAccountAndExpiresBefore(AcmeAccount account, Date expires);
}
