package com.czertainly.core.config;

import com.czertainly.api.clients.*;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.Provider;
import java.security.Security;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@PropertySource(value = ApplicationConfig.EXTERNAL_PROPERTY_SOURCE, ignoreResourceNotFound = true)
public class ApplicationConfig {
    public static final String EXTERNAL_PROPERTY_SOURCE =
            "file:${raprofiles-backend.config.dir:/etc/raprofiles-backend}/raprofiles-backend.properties";
    public static final String SECURITY_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

    @Bean
    public Provider securityProvider() {
        Provider provider = Security.getProvider(SECURITY_PROVIDER);
        if (provider == null) {
            logger.info("Registering security provider {}.", SECURITY_PROVIDER);
            provider = new BouncyCastleProvider();
            Security.addProvider(provider);
        } else {
            logger.info("Security provider {} already registered.", SECURITY_PROVIDER);
        }
        return provider;
    }

    @Bean
    public AuditorAware<String> auditorAware() {
        return new CustomAuditAware();
    }

    @Bean
    public WebClient webClient() {
        return BaseApiClient.prepareWebClient();
    }

    @Bean
    public HealthApiClient healthApiClient(WebClient webClient) {
        return new HealthApiClient(webClient);
    }

    @Bean
    public AttributeApiClient attributeApiClient(WebClient webClient) {
        return new AttributeApiClient(webClient);
    }

    @Bean
    public ConnectorApiClient connectorApiClient(WebClient webClient) {
        return new ConnectorApiClient(webClient);
    }

    @Bean
    public AuthorityInstanceApiClient authorityInstanceApiClient(WebClient webClient) {
        return new AuthorityInstanceApiClient(webClient);
    }

    @Bean
    public EntityInstanceApiClient entityInstanceApiClient(WebClient webClient) {
        return new EntityInstanceApiClient(webClient);
    }

    @Bean
    public LocationApiClient locationApiClient(WebClient webClient) {
        return new LocationApiClient(webClient);
    }

    @Bean
    public EndEntityProfileApiClient endEntityProfileApiClient(WebClient webClient) {
        return new EndEntityProfileApiClient(webClient);
    }

    @Bean
    public EndEntityApiClient endEntityApiClient(WebClient webClient) {
        return new EndEntityApiClient(webClient);
    }

    @Bean
    public CertificateApiClient certificateApiClient(WebClient webClient) {
        return new CertificateApiClient(webClient);
    }

    @Bean
    public DiscoveryApiClient discoveryApiClient(WebClient webClient) {
        return new DiscoveryApiClient(webClient);
    }

    @Bean
    public com.czertainly.api.clients.v2.CertificateApiClient certificateApiClientV2(WebClient webClient) {
        return new com.czertainly.api.clients.v2.CertificateApiClient(webClient);
    }

    @Bean
    public ComplianceApiClient complianceApiClient(WebClient webClient) { return new ComplianceApiClient(webClient); }

    @Bean
    public UserManagementApiClient userManagementApiClient(){ return new UserManagementApiClient();};

    @Bean
    public RoleManagementApiClient roleManagementApiClient(){ return new RoleManagementApiClient();};

    @Bean
    public ResourceApiClient endPointApiClient(){ return new ResourceApiClient();};
}
