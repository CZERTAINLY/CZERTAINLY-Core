package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;

import java.util.List;

public interface CoreCallbackService {

    List<JsonAttributeContent> coreGetCredentials(RequestAttributeCallback callback) throws NotFoundException, ValidationException;
}
