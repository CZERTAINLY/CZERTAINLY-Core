package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.common.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContentV2;
import com.czertainly.api.model.core.auth.AttributeResource;


import java.util.List;

public interface CoreCallbackService {

    List<ObjectAttributeContentV2> coreGetCredentials(RequestAttributeCallback callback) throws ValidationException;

    List<NameAndUuidDto> coreGetResources(RequestAttributeCallback callback, AttributeResource resource) throws NotFoundException;

}
