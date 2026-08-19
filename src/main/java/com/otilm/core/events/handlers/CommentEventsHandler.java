package com.otilm.core.events.handlers;

import com.otilm.api.exception.EventException;
import com.otilm.api.model.common.events.data.CommentEventData;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.notification.RecipientType;
import com.otilm.core.dao.entity.Comment;
import com.otilm.core.dao.entity.GroupAssociation;
import com.otilm.core.dao.repository.CommentRepository;
import com.otilm.core.dao.repository.GroupAssociationRepository;
import com.otilm.core.evaluator.TriggerEvaluator;
import com.otilm.core.events.EventContext;
import com.otilm.core.events.EventContextTriggers;
import com.otilm.core.events.EventHandler;
import com.otilm.core.messaging.model.NotificationMessage;
import com.otilm.core.messaging.model.NotificationRecipient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public abstract class CommentEventsHandler extends EventHandler<Comment> {

    protected final CommentRepository commentRepository;

    private GroupAssociationRepository groupAssociationRepository;

    protected CommentEventsHandler(CommentRepository repository, TriggerEvaluator<Comment> triggerEvaluator) {
        super(repository, triggerEvaluator);
        this.commentRepository = repository;
    }

    @Autowired
    public void setGroupAssociationRepository(GroupAssociationRepository groupAssociationRepository) {
        this.groupAssociationRepository = groupAssociationRepository;
    }

    @Override
    protected Object getEventData(Comment comment, Object eventMessageData) {
        return objectMapper.convertValue(eventMessageData, CommentEventData.class);
    }

    @Override
    protected List<EventContextTriggers> getOverridingTriggers(EventContext<Comment> eventContext, Comment comment)
            throws EventException {
        List<EventContextTriggers> eventContextTriggers = new ArrayList<>();
        for (GroupAssociation groupAssociation : groupAssociationRepository
                .findByResourceAndObjectUuid(comment.getResource(), comment.getObjectUuid())) {
            eventContextTriggers.add(fetchEventTriggers(eventContext, Resource.GROUP, groupAssociation.getGroupUuid()));
        }
        return eventContextTriggers;
    }

    protected List<NotificationRecipient> threadParticipantsExcept(UUID rootUuid, UUID actingUser) {
        return commentRepository
                .findThreadParticipantUuids(rootUuid)
                .stream()
                .filter(participant -> !participant.equals(actingUser))
                .map(participant -> new NotificationRecipient(RecipientType.USER, participant))
                .toList();
    }

    // The message carries the HOST object, not the comment: owner resolution and the notification's deep link
    // both key on the message's resource and object UUID.
    protected void publishFollowUpNotification(EventContext<Comment> eventContext, Comment comment,
            List<NotificationRecipient> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        CommentEventData eventData = (CommentEventData) eventContext.getResourceObjectsEventData().getFirst();
        applicationEventPublisher
                .publishEvent(new NotificationMessage(eventContext.getEvent(), comment.getResource(),
                        comment.getObjectUuid(), null, recipients, eventData));
    }
}
