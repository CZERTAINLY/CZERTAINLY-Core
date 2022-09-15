package com.czertainly.core.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class SyncResponseDto {

    @Schema(description = "List of end points")
    private SyncEndpointsDto endpoints;

    @Schema(description = "List ofo resources")
    private SyncResourcesDto resources;

    @Schema(description = "List of Actions")
    private SyncActionsDto actions;

    public SyncEndpointsDto getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(SyncEndpointsDto endpoints) {
        this.endpoints = endpoints;
    }

    public SyncResourcesDto getResources() {
        return resources;
    }

    public void setResources(SyncResourcesDto resources) {
        this.resources = resources;
    }

    public SyncActionsDto getActions() {
        return actions;
    }

    public void setActions(SyncActionsDto actions) {
        this.actions = actions;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("endpoints", endpoints)
                .append("resources", resources)
                .append("actions", actions)
                .toString();
    }
}
