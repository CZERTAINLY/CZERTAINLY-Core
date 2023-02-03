package com.czertainly.core.config;

import com.czertainly.api.clients.*;
import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.clients.cryptography.KeyManagementApiClient;
import com.czertainly.api.clients.cryptography.TokenInstanceApiClient;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.reactive.function.client.WebClient;

import javax.net.ssl.TrustManager;
import java.security.Provider;
import java.security.Security;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@PropertySource(value = ApplicationConfig.EXTERNAL_PROPERTY_SOURCE, ignoreResourceNotFound = true)
@ComponentScan(basePackages = "com.czertainly.core")
public class ApplicationConfig {

    @Autowired
    private TrustedCertificatesConfig trustedCertificatesConfig;

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
    public Provider securityPqcProvider() {
        Provider pqcProvider = Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME);
        if (pqcProvider == null) {
            logger.info("Registering PQC security provider {}.", BouncyCastlePQCProvider.PROVIDER_NAME);
            pqcProvider = new BouncyCastlePQCProvider();
            Security.addProvider(pqcProvider);
        } else {
            logger.info("PQC security provider {} already registered.", BouncyCastlePQCProvider.PROVIDER_NAME);
        }
        return pqcProvider;
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
    public TrustManager[] defaultTrustManagers() {
        return trustedCertificatesConfig.getDefaultTrustManagers();
    }

    @Bean
    public HealthApiClient healthApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new HealthApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public AttributeApiClient attributeApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new AttributeApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public ConnectorApiClient connectorApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new ConnectorApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public AuthorityInstanceApiClient authorityInstanceApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new AuthorityInstanceApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public EntityInstanceApiClient entityInstanceApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new EntityInstanceApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public LocationApiClient locationApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new LocationApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public EndEntityProfileApiClient endEntityProfileApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new EndEntityProfileApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public EndEntityApiClient endEntityApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new EndEntityApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public CertificateApiClient certificateApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new CertificateApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public DiscoveryApiClient discoveryApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new DiscoveryApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public com.czertainly.api.clients.v2.CertificateApiClient certificateApiClientV2(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new com.czertainly.api.clients.v2.CertificateApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public ComplianceApiClient complianceApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new ComplianceApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public UserManagementApiClient userManagementApiClient() {
        return new UserManagementApiClient();
    }

    @Bean
    public RoleManagementApiClient roleManagementApiClient() {
        return new RoleManagementApiClient();
    }

    @Bean
    public ResourceApiClient endPointApiClient() {
        return new ResourceApiClient();
    }

    //Cryptographic API Clients
    @Bean
    public TokenInstanceApiClient tokenInstanceApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new TokenInstanceApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public KeyManagementApiClient keyManagementApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new KeyManagementApiClient(webClient, defaultTrustManagers);
    }

    @Bean
    public CryptographicOperationsApiClient cryptographicOperationsApiClient(WebClient webClient, TrustManager[] defaultTrustManagers) {
        return new CryptographicOperationsApiClient(webClient, defaultTrustManagers);
    }
}
