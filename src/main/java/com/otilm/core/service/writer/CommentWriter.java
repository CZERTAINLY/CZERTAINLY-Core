package com.otilm.core.service.writer;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.dao.entity.Comment;
import com.otilm.core.dao.repository.CommentRepository;
import com.otilm.core.service.ResourceInternalService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentWriter {

    private final CommentRepository commentRepository;
    private final ResourceInternalService resourceService;

    @Autowired
    public CommentWriter(CommentRepository commentRepository, ResourceInternalService resourceService) {
        this.commentRepository = commentRepository;
        this.resourceService = resourceService;
    }

    /**
     * The host lookup shares the insert's transaction: no foreign key can hold on the polymorphic key, so a host
     * deletion committed between the orchestrator's read gate and this transaction rolls the comment back here instead
     * of committing an orphan.
     */
    @Transactional
    public Comment create(Comment comment) throws NotFoundException {
        Comment saved = commentRepository.save(comment);
        resourceService.getResourceObjectInternal(comment.getResource(), comment.getObjectUuid());
        return saved;
    }

    @Transactional
    public int resolve(UUID uuid, OffsetDateTime resolvedAt, UUID actorUuid, String actorUsername) {
        return commentRepository.resolve(uuid, resolvedAt, actorUuid, actorUsername);
    }

    @Transactional
    public int unresolve(UUID uuid) {
        return commentRepository.unresolve(uuid);
    }

    @Transactional
    public int delete(UUID uuid) {
        return commentRepository.deleteCommentByUuid(uuid);
    }

    /**
     * Locking the root serializes the deletion against concurrent replies: the row lock ({@code FOR UPDATE}) conflicts
     * with the key-share lock a reply insert takes on its parent, so the reply check below cannot be invalidated before
     * the delete commits — a root author must not erase words the thread gained meanwhile.
     */
    @Transactional
    public void deleteRoot(UUID uuid, boolean mayCascade) throws NotFoundException {
        commentRepository.findWithLockByUuid(uuid).orElseThrow(() -> new NotFoundException(Comment.class, uuid));
        if (!mayCascade && commentRepository.existsByParentUuid(uuid)) {
            throw new ValidationException(
                    "The thread gained replies; only the host object's owner or an update holder may delete it");
        }
        commentRepository.deleteCommentByUuid(uuid);
    }

    @Transactional
    public int deleteAllForObject(Resource resource, UUID objectUuid) {
        return commentRepository.deleteAllByResourceAndObjectUuid(resource, objectUuid);
    }
}
