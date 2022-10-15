package com.czertainly.core.config;

import com.czertainly.core.security.authz.ExternalMethodAuthorizationVoter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.annotation.SecuredAnnotationSecurityMetadataSource;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import java.util.List;

@Configuration
@EnableGlobalMethodSecurity
public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {
    @Autowired
    ExternalMethodAuthorizationVoter externalMethodAuthorizationVoter;

    @Autowired
    OpaSecuredAnnotationMetadataExtractor opaSecuredAnnotationMetadataExtractor;

    @Override
    protected AccessDecisionManager accessDecisionManager() {
        List<AccessDecisionVoter<?>> voters = List.of(externalMethodAuthorizationVoter);
        return new AffirmativeBased(voters);
    }

    @Override
    protected MethodSecurityMetadataSource customMethodSecurityMetadataSource() {
        return new SecuredAnnotationSecurityMetadataSource(opaSecuredAnnotationMetadataExtractor);
    }
}
