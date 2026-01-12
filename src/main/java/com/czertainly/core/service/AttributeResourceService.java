package com.czertainly.core.service;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v3.content.data.ResourceObjectContentData;

import java.util.List;

public interface AttributeResourceService {

    List<ResourceObjectContentData> getResourceObjectContent(List<SearchFilterRequestDto> filters);

}
