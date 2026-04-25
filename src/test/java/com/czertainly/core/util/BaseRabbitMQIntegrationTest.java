package com.czertainly.core.util;

import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

@ActiveProfiles("rabbitmq-integration")
public abstract class BaseRabbitMQIntegrationTest extends BaseSpringBootTest {

    // :TODO: go for 4.2
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management")
            .withVhost("czertainly");

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.virtual-host", () -> "czertainly");
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void purgeTimeQualityQueues() {
        rabbitAdmin.purgeQueue(RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG_REQUEST, false);
        rabbitAdmin.purgeQueue(RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG, false);
        rabbitAdmin.purgeQueue(RabbitMQConstants.QUEUE_TIME_QUALITY_RESULTS, false);
    }
}
