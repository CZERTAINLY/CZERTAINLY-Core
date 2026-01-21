package com.czertainly.core.provisioning;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Client for the Proxy Provisioning API.
 * Authentication is done via X-API-Key header configured in the HTTP client.
 */
public interface ProxyProvisioningApiClient {

    @PostExchange("/api/v1/proxies")
    @Retryable(
            retryFor = RestClientException.class,
            maxAttemptsExpression = "${proxy.provisioning.api.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${proxy.provisioning.api.retry.delay}",
                    maxDelayExpression = "${proxy.provisioning.api.retry.max-delay}",
                    multiplierExpression = "${proxy.provisioning.api.retry.multiplier}"
            )
    )
    void provisionProxy(@RequestBody ProxyProvisioningRequestDTO request);

    @GetExchange("/api/v1/proxies/{proxyCode}/installation")
    @Retryable(
            retryFor = RestClientException.class,
            maxAttemptsExpression = "${proxy.provisioning.api.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${proxy.provisioning.api.retry.delay}",
                    maxDelayExpression = "${proxy.provisioning.api.retry.max-delay}",
                    multiplierExpression = "${proxy.provisioning.api.retry.multiplier}"
            )
    )
    InstallationInstructionsDTO getInstallationInstructions(
            @PathVariable String proxyCode,
            @RequestParam String format
    );

    @DeleteExchange("/api/v1/proxies/{proxyCode}")
    @Retryable(
            retryFor = RestClientException.class,
            maxAttemptsExpression = "${proxy.provisioning.api.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${proxy.provisioning.api.retry.delay}",
                    maxDelayExpression = "${proxy.provisioning.api.retry.max-delay}",
                    multiplierExpression = "${proxy.provisioning.api.retry.multiplier}"
            )
    )
    void decommissionProxy(@PathVariable String proxyCode);
}
