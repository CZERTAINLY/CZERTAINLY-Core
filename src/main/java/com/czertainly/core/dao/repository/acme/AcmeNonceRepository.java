package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeNonceRepository extends JpaRepository<AcmeNonce, Long> {
    Optional<AcmeNonce> findByNonce(String nonce);

    List<AcmeNonce> findAllByExpiresBefore(Date expires);
}
