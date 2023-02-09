package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeNonce;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeNonceRepository extends SecurityFilterRepository<AcmeNonce, Long> {
    Optional<AcmeNonce> findByNonce(String nonce);

    List<AcmeNonce> findAllByExpiresBefore(Date expires);
}
