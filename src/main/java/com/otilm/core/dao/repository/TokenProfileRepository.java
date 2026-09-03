package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.TokenInstanceReference;
import com.otilm.core.dao.entity.TokenInstanceReference_;
import com.otilm.core.dao.entity.TokenProfile;
import com.otilm.core.dao.entity.TokenProfile_;
import com.otilm.core.model.crypto.ImmutableTokenProfileBasicModel;
import com.otilm.core.model.crypto.ImmutableTokenProfileFullModel;
import com.otilm.core.model.crypto.TokenProfileFullModel;
import com.otilm.core.security.authz.SecurityFilter;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenProfileRepository extends SecurityFilterRepository<TokenProfile, UUID> {

    Optional<TokenProfile> findByUuid(UUID uuid);

    Optional<TokenProfile> findByName(String name);

    boolean existsByName(String name);

    @EntityGraph(attributePaths = "tokenInstanceReference")
    @Query("SELECT profile FROM TokenProfile profile WHERE profile.uuid = :uuid")
    Optional<TokenProfile> findWithTokenInstanceByUuid(@Param("uuid") UUID uuid);

    @Query("""
            SELECT profile FROM TokenProfile profile
            JOIN FETCH profile.tokenInstanceReference token
            WHERE profile.uuid = :uuid
              AND profile.tokenInstanceReferenceUuid = :tokenUuid
              AND token.status IS NOT NULL
              AND token.connectorUuid IS NOT NULL
            """)
    Optional<TokenProfile> findWithTokenInstanceByUuidAndTokenInstanceReferenceUuid(@Param("uuid") UUID uuid,
            @Param("tokenUuid") UUID tokenUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT profile FROM TokenProfile profile WHERE profile.uuid = :uuid")
    Optional<TokenProfile> findWithLockByUuid(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT profile FROM TokenProfile profile WHERE profile.uuid = :uuid AND profile.tokenInstanceReferenceUuid = :tokenUuid")
    Optional<TokenProfile> findWithLockByUuidAndTokenInstanceReferenceUuid(@Param("uuid") UUID uuid,
            @Param("tokenUuid") UUID tokenUuid);

    default Optional<ImmutableTokenProfileBasicModel> findBasicModelByUuid(UUID uuid) {
        return findByUuid(uuid).map(ImmutableTokenProfileBasicModel::from);
    }

    default Optional<TokenProfileFullModel> findFullModelByUuidAndTokenInstanceReferenceUuid(UUID uuid,
            UUID tokenUuid) {
        return findWithTokenInstanceByUuidAndTokenInstanceReferenceUuid(uuid, tokenUuid)
                .map(ImmutableTokenProfileFullModel::from);
    }

    default List<TokenProfileFullModel> findFullModelsUsingSecurityFilter(SecurityFilter filter,
            Optional<Boolean> enabled) {
        List<TokenProfile> profiles = enabled
                .map(value -> findUsingSecurityFilter(filter, List.of("tokenInstanceReference"),
                        (Root<TokenProfile> root, CriteriaBuilder cb,
                                jakarta.persistence.criteria.CriteriaQuery<?> query) -> cb
                                        .and(cb.equal(root.get("enabled"), value),
                                                fullModelAssociationPredicate(root, cb))))
                .orElseGet(() -> findUsingSecurityFilter(filter, List.of("tokenInstanceReference"),
                        (Root<TokenProfile> root, CriteriaBuilder cb,
                                jakarta.persistence.criteria.CriteriaQuery<?> query) -> fullModelAssociationPredicate(
                                        root, cb)));
        return profiles
                .stream()
                .map(ImmutableTokenProfileFullModel::from)
                .map(value -> (TokenProfileFullModel) value)
                .toList();
    }

    private static jakarta.persistence.criteria.Predicate fullModelAssociationPredicate(Root<TokenProfile> root,
            CriteriaBuilder cb) {
        Join<TokenProfile, TokenInstanceReference> token = root.join(TokenProfile_.tokenInstanceReference);
        return cb
                .and(cb.isNotNull(token.get(TokenInstanceReference_.status)),
                        cb.isNotNull(token.get(TokenInstanceReference_.connectorUuid)));
    }

}
