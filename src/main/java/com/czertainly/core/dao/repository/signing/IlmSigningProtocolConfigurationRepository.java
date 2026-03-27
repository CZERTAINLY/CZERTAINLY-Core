package com.czertainly.core.dao.repository.signing;

import com.czertainly.core.dao.entity.signing.IlmSigningProtocolConfiguration;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IlmSigningProtocolConfigurationRepository extends SecurityFilterRepository<IlmSigningProtocolConfiguration, UUID> {
    @Modifying
    @Query("UPDATE IlmSigningProtocolConfiguration ilm SET ilm.defaultSigningProfileUuid = NULL WHERE ilm.defaultSigningProfileUuid = :signingProfileUuid")
    void clearDefaultSigningProfileUuid(UUID signingProfileUuid);
}
