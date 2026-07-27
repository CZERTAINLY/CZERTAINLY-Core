package com.otilm.core.dao.repository.signing;

import com.otilm.core.dao.entity.signing.SigningProfileVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SigningProfileVersionRepository extends JpaRepository<SigningProfileVersion, UUID> {

    @EntityGraph(attributePaths = {
            "certificate",
            "certificate.certificateContent",
            "certificate.key",
            "certificate.key.items",
    })
    Optional<SigningProfileVersion> findWithAssociationsBySigningProfileUuidAndVersion(UUID signingProfileUuid, int version);

    Optional<SigningProfileVersion> findBySigningProfileUuidAndVersion(UUID signingProfileUuid, int version);

    @Modifying
    @Query("DELETE FROM SigningProfileVersion v WHERE v.signingProfileUuid = :signingProfileUuid")
    void deleteAllBySigningProfileUuid(UUID signingProfileUuid);

    @Query("SELECT DISTINCT v.signingProfile.name FROM SigningProfileVersion v WHERE v.tokenProfileUuid = :tokenProfileUuid ORDER BY v.signingProfile.name")
    List<String> findDistinctSigningProfileNamesByTokenProfileUuid(UUID tokenProfileUuid);

    @Query("SELECT DISTINCT v.signingProfile.name FROM SigningProfileVersion v WHERE v.tokenProfileUuid = :tokenProfileUuid AND v.version = v.signingProfile.latestVersion ORDER BY v.signingProfile.name")
    List<String> findSigningProfileNamesUsingTokenProfileInLatestVersion(UUID tokenProfileUuid);
}
