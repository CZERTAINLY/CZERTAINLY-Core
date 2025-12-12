package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeNonce;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface AcmeNonceRepository extends SecurityFilterRepository<AcmeNonce, Long> {
    Optional<AcmeNonce> findByNonce(String nonce);

     long deleteByExpiresBefore(Date expires);
}
