package com.otilm.core.cbom.pqc;

import com.otilm.core.cbom.asset.identity.AssetNormalizer;
import com.otilm.core.cbom.asset.identity.IdentityTables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The ratified tables are a classpath resource; loading them once is what makes {@link PqcEvaluator} injectable. */
@Configuration
public class PqcConfig {

    @Bean
    public AssetNormalizer assetNormalizer() {
        return new AssetNormalizer(IdentityTables.load());
    }
}
