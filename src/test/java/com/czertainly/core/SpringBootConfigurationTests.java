package com.czertainly.core;

import org.mockito.Mockito;
import org.springframework.amqp.rabbit.annotation.RabbitListenerAnnotationBeanPostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class SpringBootConfigurationTests {

    @Bean
    public RabbitListenerAnnotationBeanPostProcessor rabbitListenerAnnotationBeanPostProcessor() {
        return Mockito.mock(RabbitListenerAnnotationBeanPostProcessor.class);
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        return Mockito.mock(RabbitTemplate.class);
    }

}
