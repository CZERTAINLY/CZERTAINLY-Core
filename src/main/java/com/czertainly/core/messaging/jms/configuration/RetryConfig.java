package com.czertainly.core.messaging.jms.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.JmsException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRetry
public class RetryConfig {

    @Bean
    public RetryTemplate jmsRetryTemplate(MessagingProperties messagingProperties) {
        RetryTemplate template = new RetryTemplate();

        if (messagingProperties.producer() != null && messagingProperties.producer().retry() != null &&
                messagingProperties.producer().retry().enabled()) {

            Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
            retryableExceptions.put(JmsException.class, true);

            SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                    messagingProperties.producer().retry().maxAttempts(),
                    retryableExceptions, true
            );
            template.setRetryPolicy(retryPolicy);

            ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
            backoff.setInitialInterval(messagingProperties.producer().retry().initialInterval());
            backoff.setMultiplier(messagingProperties.producer().retry().multiplier());
            backoff.setMaxInterval(messagingProperties.producer().retry().maxInterval());
            template.setBackOffPolicy(backoff);
        } else {
            RetryPolicy neverRetryPolicy = new NeverRetryPolicy();
            template.setRetryPolicy(neverRetryPolicy);
        }

        template.registerListener(new JmsRetryListener());

        return template;
    }

}
