package com.czertainly.core.logging;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.records.ResourceObjectIdentity;
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

    public List<ResourceObjectIdentity> enrichNamesAndUuids(List<ResourceObjectIdentity> resourceNamesAndUuids, Resource resource) {
        List<ResourceObjectIdentity> enrichedObjects = new ArrayList<>();
        if (resourceNamesAndUuids != null) {
            for (ResourceObjectIdentity resourceObjectIdentity : resourceNamesAndUuids) {
                if (resourceObjectIdentity.uuid() != null && resourceObjectIdentity.name() == null) {
                    try {
                        enrichedObjects.add(new ResourceObjectIdentity(resourceService.getResourceObject(resource, resourceObjectIdentity.uuid()).getName(), resourceObjectIdentity.uuid()));
                    } catch (NotFoundException ignored) {
                        // Did not manage to retrieve object name
                        enrichedObjects.add(resourceObjectIdentity);
                    }
                } else enrichedObjects.add(resourceObjectIdentity);
            }
        }
        return enrichedObjects;
    }
}
