package com.czertainly.core.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@Profile("!test")
public class BuildGitInfoContributor implements InfoContributor {

    private final GitProperties gitProperties;
    private final String version;

    public BuildGitInfoContributor(
            @Value("${app.version}") String version,
            GitProperties gitProperties) {
        this.version = version;
        this.gitProperties = gitProperties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        if (version != null && version.contains("SNAPSHOT") && gitProperties != null) {
            Map<String, Object> gitInfo = new HashMap<>();
            gitInfo.put("commit", gitProperties.getShortCommitId());
            gitInfo.put("branch", gitProperties.getBranch());

            String buildEpoch = gitProperties.get("build.time");
            if (buildEpoch != null) {
                try {
                    gitInfo.put("timestamp", Instant.ofEpochMilli(Long.parseLong(buildEpoch)));
                } catch (NumberFormatException var4) {
                    // Ignore invalid build time format
                }
            }
            builder.withDetail("build", gitInfo);
        }
    }
}
