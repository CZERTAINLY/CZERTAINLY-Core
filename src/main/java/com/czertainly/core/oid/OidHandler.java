package com.czertainly.core.oid;

import com.czertainly.api.model.core.oid.OidCategory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OidHandler {

    private OidHandler() {
    }

    private static final Map<OidCategory, Map<String, OidRecord>> oidCache = new ConcurrentHashMap<>();

    public static Map<String, OidRecord> getOidCache(OidCategory oidCategory) {
        return oidCache.get(oidCategory);
    }

    public static void cacheOidCategory(OidCategory category, Map<String, OidRecord> oidRecordMap) {
        oidCache.put(category, oidRecordMap);
    }

    public static void cacheOid(OidCategory category, String oid, OidRecord oidRecord) {
        oidCache.get(category).put(oid, oidRecord);
    }

    public static void removeCachedOid(OidCategory category, String oid) {
        oidCache.get(category).remove(oid);
    }
}
