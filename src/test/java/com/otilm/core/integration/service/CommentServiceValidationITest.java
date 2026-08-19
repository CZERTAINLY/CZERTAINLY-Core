package com.otilm.core.integration.service;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.comment.CommentCreateRequestDto;
import com.otilm.api.model.client.comment.CommentDto;
import com.otilm.api.model.client.comment.CommentResponseDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.scheduler.PaginationRequestDto;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.repository.RaProfileRepository;
import com.otilm.core.security.authz.SecuredResource;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.service.CommentExternalService;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentServiceValidationITest extends BaseSpringBootTest {

    @Autowired
    private CommentExternalService commentService;

    @Autowired
    private RaProfileRepository raProfileRepository;

    private UUID raProfileUuid;

    @BeforeEach
    void createHostObject() {
        RaProfile raProfile = new RaProfile();
        raProfile.setName("tst-ra-profile");
        raProfileUuid = raProfileRepository.save(raProfile).getUuid();
    }

    private CommentCreateRequestDto request(String body, UUID parentUuid) {
        CommentCreateRequestDto dto = new CommentCreateRequestDto();
        dto.setBody(body);
        dto.setParentUuid(parentUuid);
        return dto;
    }

    private CommentDto post(UUID objectUuid, String body, UUID parentUuid) throws NotFoundException {
        return commentService
                .createComment(SecuredResource.fromResource(Resource.RA_PROFILE), SecuredUUID.fromUUID(objectUuid),
                        request(body, parentUuid));
    }

    private CommentResponseDto list(UUID objectUuid) throws NotFoundException {
        return commentService
                .listComments(SecuredResource.fromResource(Resource.RA_PROFILE), SecuredUUID.fromUUID(objectUuid),
                        new PaginationRequestDto());
    }

    @Test
    void nonCommentableResourceIsRejected() {
        assertThatThrownBy(() -> commentService
                .createComment(SecuredResource.fromResource(Resource.USER), SecuredUUID.fromUUID(UUID.randomUUID()),
                        request("hello", null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> commentService
                .listComments(SecuredResource.fromResource(Resource.USER), SecuredUUID.fromUUID(UUID.randomUUID()),
                        new PaginationRequestDto()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void missingHostObjectIsRejected() {
        assertThatThrownBy(() -> post(UUID.randomUUID(), "hello", null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void createsRootAndReplyWithServerPopulatedAuthor() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "A **markdown** request", null);

        assertThat(root.getAuthor().getName()).isEqualTo("tst-user");
        assertThat(root.getCreatedAt()).isNotNull();
        assertThat(root.getResolved()).isFalse();

        CommentDto reply = post(raProfileUuid, "On it", root.getUuid());
        assertThat(reply.getParentUuid()).isEqualTo(root.getUuid());
        assertThat(reply.getResolved()).isNull();

        CommentResponseDto threads = list(raProfileUuid);
        assertThat(threads.getComments()).hasSize(1);
        assertThat(threads.getComments().getFirst().getReplyCount()).isEqualTo(1L);
        assertThat(commentService.listReplies(root.getUuid(), new PaginationRequestDto()).getComments())
                .extracting(CommentDto::getUuid)
                .containsExactly(reply.getUuid());
    }

    @Test
    void replyToReplyIsRejected() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        CommentDto reply = post(raProfileUuid, "reply", root.getUuid());

        assertThatThrownBy(() -> post(raProfileUuid, "reply to reply", reply.getUuid()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parentOnDifferentObjectIsRejected() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        RaProfile other = new RaProfile();
        other.setName("tst-ra-profile-2");
        UUID otherUuid = raProfileRepository.save(other).getUuid();

        assertThatThrownBy(() -> post(otherUuid, "cross-object reply", root.getUuid()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void repliesAreListedForRootsOnly() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        CommentDto reply = post(raProfileUuid, "reply", root.getUuid());

        assertThatThrownBy(() -> commentService.listReplies(reply.getUuid(), new PaginationRequestDto()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolveAndUnresolveActOnRootsOnly() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        CommentDto reply = post(raProfileUuid, "reply", root.getUuid());

        assertThatThrownBy(() -> commentService.resolveComment(reply.getUuid()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> commentService.unresolveComment(reply.getUuid()))
                .isInstanceOf(ValidationException.class);

        commentService.resolveComment(root.getUuid());
        CommentDto resolved = list(raProfileUuid).getComments().getFirst();
        assertThat(resolved.getResolved()).isTrue();
        assertThat(resolved.getResolvedBy().getName()).isEqualTo("tst-user");
        assertThat(resolved.getResolvedAt()).isNotNull();

        commentService.unresolveComment(root.getUuid());
        CommentDto reopened = list(raProfileUuid).getComments().getFirst();
        assertThat(reopened.getResolved()).isFalse();
        assertThat(reopened.getResolvedBy()).isNull();
    }

    @Test
    void deletingRootWithoutRepliesRemovesIt() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "short-lived", null);

        commentService.deleteComment(root.getUuid());

        assertThat(list(raProfileUuid).getComments()).isEmpty();
    }
}
