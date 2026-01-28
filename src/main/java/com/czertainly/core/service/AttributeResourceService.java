package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;

import java.util.UUID;

public interface AttributeResourceService {

    String getResourceObjectContent(UUID uuid) throws NotFoundException, AttributeException;

}
