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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
                .listComments(SecuredResource.fromResource(Resource.RA_PROFILE), SecuredUUID.fromUUID(objectUuid), null,
                        new PaginationRequestDto());
    }

    private CommentResponseDto listAnchoredAt(UUID objectUuid, UUID anchorUuid, int pageSize) throws NotFoundException {
        PaginationRequestDto pagination = new PaginationRequestDto();
        pagination.setItemsPerPage(pageSize);
        return commentService
                .listComments(SecuredResource.fromResource(Resource.RA_PROFILE), SecuredUUID.fromUUID(objectUuid),
                        anchorUuid, pagination);
    }

    @Test
    void anchoringOnAThreadReturnsThePageHoldingIt() throws NotFoundException {
        List<CommentDto> roots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            roots.add(post(raProfileUuid, "root " + i, null));
        }
        CommentDto onThirdPage = roots.get(5);

        CommentResponseDto anchored = listAnchoredAt(raProfileUuid, onThirdPage.getUuid(), 2);

        assertThat(anchored.getPageNumber()).isEqualTo(3);
        assertThat(anchored.getComments()).extracting(CommentDto::getUuid).contains(onThirdPage.getUuid());
    }

    @Test
    void anchoringOnAReplyReturnsThePageHoldingItsThread() throws NotFoundException {
        List<CommentDto> roots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            roots.add(post(raProfileUuid, "root " + i, null));
        }
        CommentDto thread = roots.get(4);
        CommentDto reply = post(raProfileUuid, "the reply the notification is about", thread.getUuid());

        CommentResponseDto anchored = listAnchoredAt(raProfileUuid, reply.getUuid(), 2);

        assertThat(anchored.getPageNumber()).isEqualTo(3);
        assertThat(anchored.getComments()).extracting(CommentDto::getUuid).contains(thread.getUuid());
    }

    @Test
    void aStaleAnchorLeavesTheListingOnTheRequestedPage() throws NotFoundException {
        post(raProfileUuid, "still here", null);

        CommentResponseDto anchored = listAnchoredAt(raProfileUuid, UUID.randomUUID(), 10);

        assertThat(anchored.getPageNumber()).isEqualTo(1);
        assertThat(anchored.getComments()).hasSize(1);
    }

    @Test
    void anAnchorFromAnotherObjectIsIgnored() throws NotFoundException {
        RaProfile other = new RaProfile();
        other.setName("tst-ra-profile-other");
        UUID otherUuid = raProfileRepository.save(other).getUuid();
        CommentDto foreign = post(otherUuid, "somewhere else", null);
        post(raProfileUuid, "here", null);

        CommentResponseDto anchored = listAnchoredAt(raProfileUuid, foreign.getUuid(), 10);

        assertThat(anchored.getComments()).hasSize(1);
    }

    @Test
    void anchoringOnAReplyReturnsThePageOfRepliesHoldingIt() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        List<CommentDto> replies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            replies.add(post(raProfileUuid, "reply " + i, root.getUuid()));
        }
        CommentDto target = replies.get(4);

        PaginationRequestDto pagination = new PaginationRequestDto();
        pagination.setItemsPerPage(2);
        CommentResponseDto anchored = commentService.listReplies(root.getUuid(), target.getUuid(), pagination);

        assertThat(anchored.getPageNumber()).isEqualTo(3);
        assertThat(anchored.getComments()).extracting(CommentDto::getUuid).contains(target.getUuid());
    }

    @Test
    void nonCommentableResourceIsRejected() {
        SecuredResource userResource = SecuredResource.fromResource(Resource.USER);
        SecuredUUID objectUuid = SecuredUUID.fromUUID(UUID.randomUUID());
        CommentCreateRequestDto createRequest = request("hello", null);
        PaginationRequestDto pagination = new PaginationRequestDto();
        assertThatThrownBy(() -> commentService.createComment(userResource, objectUuid, createRequest))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> commentService.listComments(userResource, objectUuid, null, pagination))
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
        assertThat(reply.getResolved()).isNull();

        CommentResponseDto threads = list(raProfileUuid);
        assertThat(threads.getComments()).hasSize(1);
        assertThat(threads.getComments().getFirst().getReplyCount()).isEqualTo(1L);
        assertThat(commentService.listReplies(root.getUuid(), null, new PaginationRequestDto()).getComments())
                .extracting(CommentDto::getUuid)
                .containsExactly(reply.getUuid());
    }

    @Test
    void replyToReplyIsRejected() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        CommentDto reply = post(raProfileUuid, "reply", root.getUuid());

        UUID replyUuid = reply.getUuid();
        assertThatThrownBy(() -> post(raProfileUuid, "reply to reply", replyUuid))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parentOnDifferentObjectIsRejected() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        RaProfile other = new RaProfile();
        other.setName("tst-ra-profile-2");
        UUID otherUuid = raProfileRepository.save(other).getUuid();

        UUID rootUuid = root.getUuid();
        assertThatThrownBy(() -> post(otherUuid, "cross-object reply", rootUuid))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void repliesAreListedForRootsOnly() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        CommentDto reply = post(raProfileUuid, "reply", root.getUuid());

        UUID replyUuid = reply.getUuid();
        PaginationRequestDto pagination = new PaginationRequestDto();
        assertThatThrownBy(() -> commentService.listReplies(replyUuid, null, pagination))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolveAndUnresolveActOnRootsOnly() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        CommentDto reply = post(raProfileUuid, "reply", root.getUuid());

        UUID replyUuid = reply.getUuid();
        assertThatThrownBy(() -> commentService.resolveComment(replyUuid)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> commentService.unresolveComment(replyUuid)).isInstanceOf(ValidationException.class);

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
    void repeatedResolutionRequestIsANoOpAndAMissingCommentIsStillNotFound() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "root", null);
        commentService.resolveComment(root.getUuid());
        OffsetDateTime firstResolvedAt = list(raProfileUuid).getComments().getFirst().getResolvedAt();

        commentService.resolveComment(root.getUuid());

        CommentDto stillResolved = list(raProfileUuid).getComments().getFirst();
        assertThat(stillResolved.getResolved()).isTrue();
        assertThat(stillResolved.getResolvedAt()).isEqualTo(firstResolvedAt);

        commentService.unresolveComment(root.getUuid());
        commentService.unresolveComment(root.getUuid());
        assertThat(list(raProfileUuid).getComments().getFirst().getResolved()).isFalse();

        UUID missingUuid = UUID.randomUUID();
        assertThatThrownBy(() -> commentService.resolveComment(missingUuid)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> commentService.unresolveComment(missingUuid)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingRootWithoutRepliesRemovesIt() throws NotFoundException {
        CommentDto root = post(raProfileUuid, "short-lived", null);

        commentService.deleteComment(root.getUuid());

        assertThat(list(raProfileUuid).getComments()).isEmpty();
    }
}
