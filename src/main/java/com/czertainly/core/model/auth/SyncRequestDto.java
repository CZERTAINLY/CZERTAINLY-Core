package com.czertainly.core.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class SyncRequestDto {
    @Schema(description = "Name of the method", required = true, example = "listClient")
    private String name;

    @Schema(description = "Request Method", required = true, example = "GET")
    private String method;

    @Schema(description = "Context of the request", required = true, example = "/v1/clients")
    private String routeTemplate;

    @Schema(description = "Name of the resource", required = true, example = "client")
    private Resource resourceName;

    @Schema(description = "Action Name", required = true, example = "list")
    private ResourceAction actionName;

    @Schema(description = "Is endpoint for listing objects flag - true = Yes; false = No", required = true, example = "false", defaultValue = "false")
    private boolean isListingEndpoint = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRouteTemplate() {
        return routeTemplate;
    }

    public void setRouteTemplate(String routeTemplate) {
        this.routeTemplate = routeTemplate;
    }

    public Resource getResourceName() {
        return resourceName;
    }

    public void setResourceName(Resource resourceName) {
        this.resourceName = resourceName;
    }

    public ResourceAction getActionName() {
        return actionName;
    }

    public void setActionName(ResourceAction actionName) {
        this.actionName = actionName;
    }

    public boolean getIsListingEndpoint() {
        return isListingEndpoint;
    }

    public void setIsListingEndpoint(boolean isListingEndpoint) {
        isListingEndpoint = isListingEndpoint;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", name)
                .append("method", method)
                .append("routeTemplate", routeTemplate)
                .append("resourceName", resourceName)
                .append("actionName", actionName)
                .append("isListingEndpoint", isListingEndpoint)
                .toString();
    }
}
