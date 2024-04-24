package com.czertainly.core.api.cmp.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Objects;

public class CryptoUtil {

    private static Provider BOUNCY_CASTLE_PROVIDER;
    public static Provider getBouncyCastleProvider() {
        if (BOUNCY_CASTLE_PROVIDER == null) {
            BOUNCY_CASTLE_PROVIDER = BouncyCastleInitializer.getInstance();
        }
        return BOUNCY_CASTLE_PROVIDER;
    }

    private static class BouncyCastleInitializer {
        private static synchronized Provider getInstance() {
            return Arrays.stream(Security.getProviders())
                    .filter(it -> Objects.equals(it.getName(), BouncyCastleProvider.PROVIDER_NAME))
                    .findFirst()
                    .orElseGet(BouncyCastleProvider::new);
        }
    }
}
