package com.otilm.core.model.comment;

import com.otilm.api.model.common.events.data.CommentEventData;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit data of a thread root's deletion. Comments cannot be edited, so the audit record is the only place the text of
 * a deleted comment survives; the replies removed with the root are carried here, each with its verbatim body, so that
 * no deleted text goes unrecorded. Audit-only, which is why the field is not on the event payload itself.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class CommentDeletionData extends CommentEventData {

    private List<CommentEventData> cascadedReplies;
}
