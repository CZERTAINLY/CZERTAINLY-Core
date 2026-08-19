package com.otilm.core.service.writer;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.dao.entity.Comment;
import com.otilm.core.dao.repository.CommentRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentWriter {

    private final CommentRepository commentRepository;

    @Autowired
    public CommentWriter(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Transactional
    public Comment create(Comment comment) {
        return commentRepository.save(comment);
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

    @Transactional
    public int deleteAllForObject(Resource resource, UUID objectUuid) {
        return commentRepository.deleteAllByResourceAndObjectUuid(resource, objectUuid);
    }
}
