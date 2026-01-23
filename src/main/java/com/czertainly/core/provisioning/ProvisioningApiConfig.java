package com.czertainly.core.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Configuration for the provisioning API clients.
 */
@Configuration
@EnableConfigurationProperties(ProvisioningApiProperties.class)
@RequiredArgsConstructor
public class ProvisioningApiConfig {

    private final ProvisioningApiProperties provisioningApiProperties;

    @Bean
    public RestClient provisioningRestClient() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(provisioningApiProperties.connectTimeout())
            .withReadTimeout(provisioningApiProperties.readTimeout());

        return RestClient.builder()
            .baseUrl(provisioningApiProperties.url())
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .defaultHeader("X-API-Key", provisioningApiProperties.apiKey())
            .build();
    }

    @Bean
    public ProxyProvisioningApiClient proxyProvisioningApiClient(RestClient provisioningRestClient) {
        HttpServiceProxyFactory httpServiceProxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(provisioningRestClient))
            .build();
        return httpServiceProxyFactory.createClient(ProxyProvisioningApiClient.class);
    }

    @Bean
    public TrustedCertificateProvisioningApiClient trustedCertificateProvisioningApiClient(RestClient provisioningRestClient) {
        HttpServiceProxyFactory httpServiceProxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(provisioningRestClient))
            .build();
        return httpServiceProxyFactory.createClient(TrustedCertificateProvisioningApiClient.class);
    }
}