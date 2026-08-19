package com.otilm.core.dao.repository;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.dao.entity.Comment;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends SecurityFilterRepository<Comment, UUID> {

    Page<Comment> findByResourceAndObjectUuidAndParentUuidIsNullOrderByCreatedAtAsc(Resource resource, UUID objectUuid,
            Pageable pageable);

    Page<Comment> findByParentUuidOrderByCreatedAtAsc(UUID parentUuid, Pageable pageable);

    @Query("SELECT c.parentUuid, COUNT(c) FROM Comment c WHERE c.parentUuid IN :rootUuids GROUP BY c.parentUuid")
    List<Object[]> countRepliesByRoots(@Param("rootUuids") Collection<UUID> rootUuids);

    boolean existsByParentUuid(UUID parentUuid);

    boolean existsByResourceAndObjectUuid(Resource resource, UUID objectUuid);

    @Modifying
    @Query("UPDATE Comment c SET c.resolvedAt = :resolvedAt, c.resolvedByUuid = :actorUuid, "
            + "c.resolvedByUsername = :actorUsername WHERE c.uuid = :uuid")
    int resolve(@Param("uuid") UUID uuid, @Param("resolvedAt") OffsetDateTime resolvedAt,
            @Param("actorUuid") UUID actorUuid, @Param("actorUsername") String actorUsername);

    @Modifying
    @Query("UPDATE Comment c SET c.resolvedAt = NULL, c.resolvedByUuid = NULL, c.resolvedByUsername = NULL "
            + "WHERE c.uuid = :uuid")
    int unresolve(@Param("uuid") UUID uuid);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.uuid = :uuid")
    int deleteCommentByUuid(@Param("uuid") UUID uuid);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.resource = :resource AND c.objectUuid = :objectUuid")
    int deleteAllByResourceAndObjectUuid(@Param("resource") Resource resource, @Param("objectUuid") UUID objectUuid);
}
