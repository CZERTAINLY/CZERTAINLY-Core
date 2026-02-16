package com.czertainly.core.messaging.jms.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "messaging.concurrency")
@Validated
public record MessagingConcurrencyProperties(
        @NotBlank String actions,
        @NotBlank String auditLogs,
        @NotBlank String events,
        @NotBlank String notifications,
        @NotBlank String scheduler,
        @NotBlank String validation
) {

}

