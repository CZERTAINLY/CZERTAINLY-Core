package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.AttributeCallback;
import com.czertainly.api.model.common.NameAndUuidDto;

import java.util.List;

public interface CoreCallbackService {

    List<NameAndUuidDto> coreGetCredentials(AttributeCallback callback) throws NotFoundException, ValidationException;
}
