package com.czertainly.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.Ordered;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig {

    public static final String SIGNING_PROFILES_CACHE = "signingProfiles";
    public static final String TSP_PROFILES_CACHE = "tspProfiles";
    public static final String CERTIFICATE_CHAIN_CACHE = "certificateChain";
    public static final String SYSTEM_USER_AUTH_CACHE = "systemUserAuth";
    public static final String FORMATTER_CONNECTOR_CACHE = "formatterConnector";
    public static final String CRYPTOGRAPHIC_KEY_ITEM_CACHE = "cryptographicKeyItem";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(SIGNING_PROFILES_CACHE, TSP_PROFILES_CACHE, CERTIFICATE_CHAIN_CACHE, SYSTEM_USER_AUTH_CACHE, FORMATTER_CONNECTOR_CACHE, CRYPTOGRAPHIC_KEY_ITEM_CACHE);
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500));
        return mgr;
    }
}