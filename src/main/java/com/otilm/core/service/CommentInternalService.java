package com.otilm.core.service;

import com.otilm.api.model.core.auth.Resource;
import java.util.UUID;

public interface CommentInternalService {

    /**
     * Removes every comment thread (roots and replies) attached to the given object. Called from host-object delete
     * paths so no orphaned rows survive the object they annotate.
     */
    void removeObjectComments(Resource resource, UUID objectUuid);
}
