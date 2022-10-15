package com.czertainly.core.security.authz;

public class SecurityFilter {
    private SecurityResourceFilter resourceFilter;

    private SecurityResourceFilter parentResourceFilter;

    private String parentRefProperty;

    public SecurityResourceFilter getResourceFilter() {
        return resourceFilter;
    }

    public static SecurityFilter create() {
        return new SecurityFilter();
    }

    public void setResourceFilter(SecurityResourceFilter resourceFilter) {
        this.resourceFilter = resourceFilter;
    }

    public SecurityResourceFilter getParentResourceFilter() {
        return parentResourceFilter;
    }

    public void setParentResourceFilter(SecurityResourceFilter parentResourceFilter) {
        this.parentResourceFilter = parentResourceFilter;
    }
    public String getParentRefProperty() {
        return parentRefProperty;
    }

    public void setParentRefProperty(String parentRefProperty) {
        this.parentRefProperty = parentRefProperty;
    }
}
