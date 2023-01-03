package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyContent;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface CryptographicKeyContentRepository extends SecurityFilterRepository<CryptographicKeyContent, UUID> {

    Optional<CryptographicKeyContent> findByUuid(UUID uuid);

    List<CryptographicKeyContent> findByCryptographicKey(CryptographicKey cryptographicKey);
}
