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
 * Configuration for the proxy provisioning API client.
 */
@Configuration
@EnableConfigurationProperties(ProxyProvisioningApiProperties.class)
@RequiredArgsConstructor
public class ProxyProvisioningApiConfig {

    private final ProxyProvisioningApiProperties proxyProvisioningApiProperties;

    @Bean
    public RestClient provisioningRestClient() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(proxyProvisioningApiProperties.connectTimeout())
            .withReadTimeout(proxyProvisioningApiProperties.readTimeout());

        return RestClient.builder()
            .baseUrl(proxyProvisioningApiProperties.url())
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .defaultHeader("X-API-Key", proxyProvisioningApiProperties.apiKey())
            .build();
    }

    @Bean
    public ProxyProvisioningApiClient provisioningApiClient(RestClient provisioningRestClient) {
        HttpServiceProxyFactory httpServiceProxyFactory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(provisioningRestClient))
            .build();
        return httpServiceProxyFactory.createClient(ProxyProvisioningApiClient.class);
    }
}
