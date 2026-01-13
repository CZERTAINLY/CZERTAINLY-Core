package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.attribute.v3.content.data.ResourceObjectContentData;

import java.util.UUID;

public interface AttributeResourceService {

    ResourceObjectContentData getResourceObjectContent(UUID uuid) throws NotFoundException;

}
