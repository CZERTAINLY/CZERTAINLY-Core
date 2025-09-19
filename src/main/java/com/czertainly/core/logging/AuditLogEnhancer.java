package com.czertainly.core.logging;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.records.NameAndUuid;
import com.czertainly.core.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class AuditLogEnhancer {

    private ResourceService resourceService;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public List<NameAndUuid> enrichNamesAndUuids(List<NameAndUuid> resourceNamesAndUuids, Resource resource) {
        if (resourceNamesAndUuids != null) {
            for (NameAndUuid nameAndUuid : resourceNamesAndUuids) {
                if (nameAndUuid.getUuid() != null && nameAndUuid.getName() == null) {
                    try {
                        nameAndUuid.setName(resourceService.getResourceObject(resource, nameAndUuid.getUuid()).getName());
                    } catch (NotFoundException ignored) {
                        // Did not manage to retrieve object name
                    }
                }
            }
        }
        return resourceNamesAndUuids;
    }
}
