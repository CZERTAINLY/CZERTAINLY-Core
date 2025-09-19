package com.czertainly.core.logging;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.records.NameAndUuid;
import com.czertainly.core.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AuditLogEnhancer {

    private ResourceService resourceService;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public List<NameAndUuid> enrichNamesAndUuids(List<NameAndUuid> resourceNamesAndUuids, Resource resource) {
        List<NameAndUuid> enrichedObjects = new ArrayList<>();
        if (resourceNamesAndUuids != null) {
            for (NameAndUuid nameAndUuid : resourceNamesAndUuids) {
                if (nameAndUuid.uuid() != null && nameAndUuid.name() == null) {
                    try {
                        enrichedObjects.add(new NameAndUuid(resourceService.getResourceObject(resource, nameAndUuid.uuid()).getName(), nameAndUuid.uuid()));
                    } catch (NotFoundException ignored) {
                        // Did not manage to retrieve object name
                        enrichedObjects.add(nameAndUuid);
                    }
                } else enrichedObjects.add(nameAndUuid);
            }
        }
        return enrichedObjects;
    }
}
