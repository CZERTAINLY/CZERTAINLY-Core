package com.czertainly.core.security.authn.client;

import com.czertainly.core.config.CacheConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class CzertainlyAuthenticationCache implements AuthenticationCache {

    private final CacheManager cacheManager;

    // userUuid → set of `jti` claims cached for that user; enables targeted per-user token eviction.
    // We cache using jti, but during eviction, jti is not known - only userUuid is known.
    private final ConcurrentHashMap<String, Set<String>> userJtiIndex = new ConcurrentHashMap<>();

    @Autowired
    public CzertainlyAuthenticationCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    @Cacheable(value = CacheConfig.SYSTEM_USER_AUTH_CACHE, key = "#username", unless = "#result.anonymous")
    public AuthenticationInfo getOrAuthenticateSystemUser(String username, Supplier<AuthenticationInfo> loader) {
        return loader.get();
    }

    @Override
    @Cacheable(value = CacheConfig.USER_UUID_AUTH_CACHE, key = "#userUuid.toString()", unless = "#result.anonymous")
    public AuthenticationInfo getOrAuthenticateByUserUuid(UUID userUuid, Supplier<AuthenticationInfo> loader) {
        return loader.get();
    }

    @Override
    @Cacheable(value = CacheConfig.CERTIFICATE_AUTH_CACHE, key = "#thumbprint", unless = "#result.anonymous")
    public AuthenticationInfo getOrAuthenticateByCertificate(String thumbprint, Supplier<AuthenticationInfo> loader) {
        return loader.get();
    }

    // Manual caching (instead of @Cacheable) keeps the userJtiIndex in sync, enabling targeted
    // per-user eviction via evictTokensByUserUuid().
    @Override
    public AuthenticationInfo getOrAuthenticateByToken(String jti, Supplier<AuthenticationInfo> loader) {
        if (jti == null) {
            return loader.get();
        }
        Cache tokenCache = cacheManager.getCache(CacheConfig.TOKEN_AUTH_CACHE);
        assert tokenCache != null;
        Cache.ValueWrapper cached = tokenCache.get(jti);
        if (cached != null) {
            return (AuthenticationInfo) cached.get();
        }
        AuthenticationInfo result = loader.get();
        if (!result.isAnonymous()) {
            tokenCache.put(jti, result);
            userJtiIndex.computeIfAbsent(result.getUserUuid(), k -> ConcurrentHashMap.newKeySet()).add(jti);
        }
        return result;
    }

    @Override
    public void evictByUserUuid(String userUuid, String certFingerprint) {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.USER_UUID_AUTH_CACHE)).evict(userUuid);
        evictTokensByUserUuid(userUuid);
        if (certFingerprint != null) {
            evictCertificateByFingerprint(certFingerprint);
        }
    }

    @Override
    public void evictAll() {
        userJtiIndex.clear();
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.SYSTEM_USER_AUTH_CACHE)).clear();
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.USER_UUID_AUTH_CACHE)).clear();
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.CERTIFICATE_AUTH_CACHE)).clear();
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.TOKEN_AUTH_CACHE)).clear();
    }

    // Looks up jtis for the user in the secondary index and evicts each one individually.
    // No-op if the user has no cached tokens.
    private void evictTokensByUserUuid(String userUuid) {
        Set<String> jtis = userJtiIndex.remove(userUuid);
        if (jtis == null) return;
        Cache tokenCache = cacheManager.getCache(CacheConfig.TOKEN_AUTH_CACHE);
        assert tokenCache != null;
        jtis.forEach(tokenCache::evict);
    }

    @Override
    public void evictByCertificateFingerprint(String certFingerprint) {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.CERTIFICATE_AUTH_CACHE)).evict(certFingerprint);
    }

    private void evictCertificateByFingerprint(String fingerprint) {
        evictByCertificateFingerprint(fingerprint);
    }

}
