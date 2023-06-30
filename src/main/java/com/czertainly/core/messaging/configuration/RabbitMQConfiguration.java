package com.czertainly.core.messaging.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange czertainlyExchange() {
        return new DirectExchange(RabbitMQConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue queueEvents() {
        return new Queue(RabbitMQConstants.QUEUE_EVENTS_NAME, true);
    }

    @Bean
    public Queue queueNotifications() {
        return new Queue(RabbitMQConstants.QUEUE_NOTIFICATIONS_NAME, true);
    }

    @Bean
    public Queue queueScheduler() {
        return new Queue(RabbitMQConstants.QUEUE_SCHEDULER_NAME, true);
    }

    @Bean
    public Binding eventQueueBinding() {
        return BindingBuilder.bind(queueEvents()).to(czertainlyExchange()).with(RabbitMQConstants.EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding notificationQueueBinding() {
        return BindingBuilder.bind(queueNotifications()).to(czertainlyExchange()).with(RabbitMQConstants.NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding schedulerQueueBinding() {
        return BindingBuilder.bind(queueScheduler()).to(czertainlyExchange()).with(RabbitMQConstants.SCHEDULER_ROUTING_KEY);
    }


}
