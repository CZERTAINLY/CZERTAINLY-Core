package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.RequestAttributeCallback;

import java.util.List;

public interface CoreCallbackService {

    List<NameAndUuidDto> coreGetCredentials(RequestAttributeCallback callback) throws NotFoundException, ValidationException;
}
