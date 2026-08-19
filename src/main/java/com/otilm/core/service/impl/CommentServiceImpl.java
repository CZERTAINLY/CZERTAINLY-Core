package com.otilm.core.service.impl;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.comment.CommentCreateRequestDto;
import com.otilm.api.model.client.comment.CommentDto;
import com.otilm.api.model.client.comment.CommentResponseDto;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.common.events.data.CommentEventData;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.scheduler.PaginationRequestDto;
import com.otilm.core.aop.AuditOperationDataOverride;
import com.otilm.core.dao.entity.Comment;
import com.otilm.core.dao.repository.CommentRepository;
import com.otilm.core.dao.repository.OwnerAssociationRepository;
import com.otilm.core.mapper.comment.CommentMapper;
import com.otilm.core.messaging.jms.producers.EventProducer;
import com.otilm.core.messaging.model.EventMessage;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.security.authz.AnyPrincipalEndpoint;
import com.otilm.core.security.authz.AuthorizationEnforcer;
import com.otilm.core.security.authz.ExternalAuthorizationDynamic;
import com.otilm.core.security.authz.SecuredResource;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.service.CommentExternalService;
import com.otilm.core.service.CommentInternalService;
import com.otilm.core.service.ResourceExtensionService;
import com.otilm.core.service.writer.CommentWriter;
import com.otilm.core.util.AuthHelper;
import com.otilm.core.util.RequestValidatorHelper;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

@Service
public class CommentServiceImpl implements CommentExternalService, CommentInternalService {

    private CommentRepository commentRepository;
    private CommentWriter commentWriter;
    private Map<String, ResourceExtensionService> resourceExtensionServices;
    private AuthorizationEnforcer authorizationEnforcer;
    private OwnerAssociationRepository ownerAssociationRepository;
    private EventProducer eventProducer;
    private AuditOperationDataOverride auditOperationDataOverride;

    @Autowired
    public void setCommentRepository(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Autowired
    public void setCommentWriter(CommentWriter commentWriter) {
        this.commentWriter = commentWriter;
    }

    @Lazy
    @Autowired
    public void setResourceExtensionServices(Map<String, ResourceExtensionService> resourceExtensionServices) {
        this.resourceExtensionServices = resourceExtensionServices;
    }

    @Autowired
    public void setAuthorizationEnforcer(AuthorizationEnforcer authorizationEnforcer) {
        this.authorizationEnforcer = authorizationEnforcer;
    }

    @Autowired
    public void setOwnerAssociationRepository(OwnerAssociationRepository ownerAssociationRepository) {
        this.ownerAssociationRepository = ownerAssociationRepository;
    }

    @Autowired
    public void setEventProducer(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Autowired
    public void setAuditOperationDataOverride(AuditOperationDataOverride auditOperationDataOverride) {
        this.auditOperationDataOverride = auditOperationDataOverride;
    }

    @Override
    @ExternalAuthorizationDynamic(action = ResourceAction.DETAIL)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommentResponseDto listComments(SecuredResource resource, SecuredUUID objectUuid,
            PaginationRequestDto pagination) throws NotFoundException {
        Resource hostResource = validateCommentable(resource);
        resourceExtensionService(hostResource).getResourceObjectInternal(objectUuid.getValue());
        RequestValidatorHelper.revalidatePaginationRequestDto(pagination);

        Page<Comment> roots = commentRepository
                .findByResourceAndObjectUuidAndParentUuidIsNullOrderByCreatedAtAsc(hostResource, objectUuid.getValue(),
                        PageRequest.of(pagination.getPageNumber() - 1, pagination.getItemsPerPage()));
        List<UUID> rootUuids = roots.getContent().stream().map(Comment::getUuid).toList();
        Map<UUID, List<Comment>> repliesByRoot = rootUuids.isEmpty()
                ? Map.of()
                : commentRepository
                        .findByParentUuidInOrderByCreatedAtAsc(rootUuids)
                        .stream()
                        .collect(Collectors.groupingBy(Comment::getParentUuid));
        List<CommentDto> threads = roots
                .getContent()
                .stream()
                .map(root -> CommentMapper.toDto(root, repliesByRoot.get(root.getUuid())))
                .toList();
        return CommentMapper.toResponseDto(roots, threads);
    }

    @Override
    @ExternalAuthorizationDynamic(action = ResourceAction.COMMENT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommentDto createComment(SecuredResource resource, SecuredUUID objectUuid, CommentCreateRequestDto request)
            throws NotFoundException {
        Resource hostResource = validateCommentable(resource);
        NameAndUuidDto hostObject = resourceExtensionService(hostResource).getResourceObjectExternal(objectUuid);
        if (request.getParentUuid() != null) {
            validateParent(hostResource, objectUuid.getValue(), request.getParentUuid());
        }
        NameAndUuidDto actor = AuthHelper.getUserIdentification();

        Comment comment = new Comment();
        comment.setResource(hostResource);
        comment.setObjectUuid(objectUuid.getValue());
        comment.setParentUuid(request.getParentUuid());
        comment.setAuthorUuid(UUID.fromString(actor.getUuid()));
        comment.setAuthorUsername(actor.getName());
        comment.setBody(request.getBody());
        Comment saved = commentWriter.create(comment);

        CommentEventData eventData = baseEventData(saved, hostObject.getName());
        recordAuditData(eventData);
        publishAfterCommit(new EventMessage(ResourceEvent.COMMENT_CREATED, Resource.COMMENT, saved.getUuid(), null,
                null, eventData, saved.getAuthorUuid(), null));
        return CommentMapper.toDto(saved, null);
    }

    @Override
    @AnyPrincipalEndpoint
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void resolveComment(UUID uuid) throws NotFoundException {
        changeResolution(uuid, true);
    }

    @Override
    @AnyPrincipalEndpoint
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void unresolveComment(UUID uuid) throws NotFoundException {
        changeResolution(uuid, false);
    }

    @Override
    @AnyPrincipalEndpoint
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteComment(UUID uuid) throws NotFoundException {
        Comment comment = getComment(uuid);
        NameAndUuidDto hostObject = readGate(comment);
        NameAndUuidDto actor = AuthHelper.getUserIdentification();
        boolean isAuthor = actor.getUuid().equals(comment.getAuthorUuid().toString());
        boolean rootWithReplies = comment.getParentUuid() == null && commentRepository.existsByParentUuid(uuid);

        // A root author must not be able to erase other users' words: once a root has replies, only the host
        // object's owner or an update holder may delete it (the delete cascades to the replies).
        boolean permitted = rootWithReplies
                ? isHostObjectOwner(comment, actor) || holdsHostObjectUpdate(comment)
                : isAuthor || isHostObjectOwner(comment, actor) || holdsHostObjectUpdate(comment);
        if (!permitted) {
            throw new AccessDeniedException("Access denied to delete comment %s on %s %s"
                    .formatted(uuid, comment.getResource().getCode(), comment.getObjectUuid()));
        }
        recordAuditData(baseEventData(comment, hostObject.getName()));
        commentWriter.delete(uuid);
    }

    @Override
    public void removeObjectComments(Resource resource, UUID objectUuid) {
        commentWriter.deleteAllForObject(resource, objectUuid);
    }

    private void changeResolution(UUID uuid, boolean resolved) throws NotFoundException {
        Comment comment = getComment(uuid);
        if (comment.getParentUuid() != null) {
            throw new ValidationException("Only a thread root can be resolved or reopened");
        }
        NameAndUuidDto hostObject = readGate(comment);
        NameAndUuidDto actor = AuthHelper.getUserIdentification();
        if (!actor.getUuid().equals(comment.getAuthorUuid().toString())) {
            authorizationEnforcer
                    .enforce(comment.getResource(), ResourceAction.COMMENT,
                            SecuredUUID.fromUUID(comment.getObjectUuid()));
        }

        OffsetDateTime changedAt = OffsetDateTime.now();
        if (resolved) {
            commentWriter.resolve(uuid, changedAt, UUID.fromString(actor.getUuid()), actor.getName());
        } else {
            commentWriter.unresolve(uuid);
        }

        CommentEventData eventData = baseEventData(comment, hostObject.getName());
        eventData.setResolved(resolved);
        eventData.setResolvedByUuid(UUID.fromString(actor.getUuid()));
        eventData.setResolvedByUsername(actor.getName());
        eventData.setResolvedAt(changedAt);
        recordAuditData(eventData);
        publishAfterCommit(new EventMessage(ResourceEvent.COMMENT_RESOLVED, Resource.COMMENT, uuid, null, null,
                eventData, UUID.fromString(actor.getUuid()), null));
    }

    private Resource validateCommentable(SecuredResource resource) {
        Resource hostResource = resource.getResource();
        if (!hostResource.commentable()) {
            throw new ValidationException("Resource %s does not support comments".formatted(hostResource.getLabel()));
        }
        return hostResource;
    }

    private ResourceExtensionService resourceExtensionService(Resource resource) {
        return resourceExtensionServices.get(resource.getCode());
    }

    private Comment getComment(UUID uuid) throws NotFoundException {
        return commentRepository
                .findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Comment.class, uuid));
    }

    private NameAndUuidDto readGate(Comment comment) throws NotFoundException {
        return resourceExtensionService(comment.getResource())
                .getResourceObjectExternal(SecuredUUID.fromUUID(comment.getObjectUuid()));
    }

    private void validateParent(Resource resource, UUID objectUuid, UUID parentUuid) throws NotFoundException {
        Comment parent = commentRepository
                .findByUuid(SecuredUUID.fromUUID(parentUuid))
                .orElseThrow(() -> new NotFoundException(Comment.class, parentUuid));
        if (parent.getParentUuid() != null) {
            throw new ValidationException("Threads are one level deep: a reply cannot be replied to");
        }
        if (parent.getResource() != resource || !parent.getObjectUuid().equals(objectUuid)) {
            throw new ValidationException("The parent comment belongs to a different object");
        }
    }

    private boolean isHostObjectOwner(Comment comment, NameAndUuidDto actor) {
        var ownerAssociation = ownerAssociationRepository
                .findByResourceAndObjectUuid(comment.getResource(), comment.getObjectUuid());
        return ownerAssociation != null && ownerAssociation.getOwnerUuid().toString().equals(actor.getUuid());
    }

    private boolean holdsHostObjectUpdate(Comment comment) {
        try {
            authorizationEnforcer
                    .enforce(comment.getResource(), ResourceAction.UPDATE,
                            SecuredUUID.fromUUID(comment.getObjectUuid()));
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }

    private CommentEventData baseEventData(Comment comment, String objectName) {
        CommentEventData eventData = new CommentEventData();
        eventData.setCommentUuid(comment.getUuid());
        eventData.setParentUuid(comment.getParentUuid());
        eventData.setResource(comment.getResource());
        eventData.setObjectUuid(comment.getObjectUuid());
        eventData.setObjectName(objectName);
        eventData.setAuthorUuid(comment.getAuthorUuid());
        eventData.setAuthorUsername(comment.getAuthorUsername());
        eventData.setCreatedAt(comment.getCreatedAt());
        eventData.setBody(comment.getBody());
        return eventData;
    }

    // The override is request-scoped; outside an HTTP request (internal callers, tests hitting the service
    // directly) there is no audit frame to enrich.
    private void recordAuditData(Serializable data) {
        if (RequestContextHolder.getRequestAttributes() != null) {
            auditOperationDataOverride.set(data);
        }
    }

    private void publishAfterCommit(EventMessage eventMessage) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventProducer.produceMessage(eventMessage);
                }
            });
        } else {
            eventProducer.produceMessage(eventMessage);
        }
    }
}
