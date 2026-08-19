package com.otilm.core.integration.dao.repository;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.dao.entity.Comment;
import com.otilm.core.dao.repository.CommentRepository;
import com.otilm.core.util.BaseSpringBootTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentRepositoryITest extends BaseSpringBootTest {

    @Autowired
    private CommentRepository commentRepository;

    private Comment newComment(UUID objectUuid, UUID parentUuid) {
        Comment comment = new Comment();
        comment.setResource(Resource.RA_PROFILE);
        comment.setObjectUuid(objectUuid);
        comment.setParentUuid(parentUuid);
        comment.setAuthorUuid(UUID.randomUUID());
        comment.setAuthorUsername("tst-user");
        comment.setBody("A **markdown** body");
        return comment;
    }

    @Test
    void persistsRootAndReply() {
        UUID objectUuid = UUID.randomUUID();
        Comment root = commentRepository.saveAndFlush(newComment(objectUuid, null));
        Comment reply = commentRepository.saveAndFlush(newComment(objectUuid, root.getUuid()));

        assertThat(root.getCreatedAt()).isNotNull();
        assertThat(commentRepository.findByParentUuidInOrderByCreatedAtAsc(List.of(root.getUuid())))
                .extracting(Comment::getUuid)
                .containsExactly(reply.getUuid());
        assertThat(commentRepository.existsByParentUuid(root.getUuid())).isTrue();
    }

    @Test
    void pagesThreadRootsOnly() {
        UUID objectUuid = UUID.randomUUID();
        Comment root = commentRepository.saveAndFlush(newComment(objectUuid, null));
        commentRepository.saveAndFlush(newComment(objectUuid, root.getUuid()));

        Page<Comment> page = commentRepository
                .findByResourceAndObjectUuidAndParentUuidIsNullOrderByCreatedAtAsc(Resource.RA_PROFILE, objectUuid,
                        PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getUuid()).isEqualTo(root.getUuid());
    }

    @Test
    void checkConstraintRejectsResolvedReply() {
        UUID objectUuid = UUID.randomUUID();
        Comment root = commentRepository.saveAndFlush(newComment(objectUuid, null));
        Comment reply = newComment(objectUuid, root.getUuid());
        reply.setResolvedAt(OffsetDateTime.now());
        reply.setResolvedByUuid(UUID.randomUUID());
        reply.setResolvedByUsername("tst-user");

        assertThatThrownBy(() -> commentRepository.saveAndFlush(reply))
                .hasStackTraceContaining("ck_comment_reply_not_resolved");
    }

    @Test
    void deletingRootCascadesToReplies() {
        UUID objectUuid = UUID.randomUUID();
        Comment root = commentRepository.saveAndFlush(newComment(objectUuid, null));
        commentRepository.saveAndFlush(newComment(objectUuid, root.getUuid()));

        commentRepository.delete(root);
        commentRepository.flush();

        assertThat(commentRepository.count()).isZero();
    }
}
