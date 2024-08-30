package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContent;

import java.util.List;

public interface CoreCallbackService {

    List<ObjectAttributeContent> coreGetCredentials(RequestAttributeCallback callback) throws ValidationException;
}
