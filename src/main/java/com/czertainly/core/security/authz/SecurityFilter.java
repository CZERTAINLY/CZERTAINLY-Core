package com.czertainly.core.security.authz;

import java.util.ArrayList;
import java.util.List;

public class SecurityFilter {

    /**
     * List of object uuids user can access
     */
    private final List<String> allowedObjects;

    /**
     * List of object uuids user can not access
     */
    private final List<String> forbiddenObjects;

    /**
     * Specifies whether the user has access to all objects or only those explicitly allowed.
     * When true, the user can access only objects to which access is explicitly allowed.
     * When false, the user can access all objects except those to which access is explicitly forbidden.
     */
    private boolean areOnlySpecificObjectsAllowed;

    protected SecurityFilter() {
        this(new ArrayList<>(), new ArrayList<>(), false);
    }

    public SecurityFilter(List<String> allowedObjects, List<String> forbiddenObjects, boolean areOnlySpecificObjectsAllowed) {
        this.allowedObjects = allowedObjects;
        this.forbiddenObjects = forbiddenObjects;
        this.areOnlySpecificObjectsAllowed = areOnlySpecificObjectsAllowed;
    }

    public static SecurityFilter create() {
        return new SecurityFilter();
    }

    public List<String> getAllowedObjects() {
        return allowedObjects;
    }

    public List<String> getForbiddenObjects() {
        return forbiddenObjects;
    }

    public void addAllowedObjects(List<String> objectUUIDs) {
        this.allowedObjects.addAll(objectUUIDs);
    }

    public void addDeniedObjects(List<String> objectUUIDs) {
        this.forbiddenObjects.addAll(objectUUIDs);
    }

    public boolean areOnlySpecificObjectsAllowed() {
        return areOnlySpecificObjectsAllowed;
    }

    public void setAreOnlySpecificObjectsAllowed(boolean areOnlySpecificObjectsAllowed) {
        this.areOnlySpecificObjectsAllowed = areOnlySpecificObjectsAllowed;
    }
}
